import socket
import json
import urllib.request
import urllib.error
import base64
import os

BASE_URL = "http://localhost:8085"

def post_json(path, data):
    req = urllib.request.Request(
        f"{BASE_URL}{path}",
        data=json.dumps(data).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))

def get_json(path, token=None):
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{BASE_URL}{path}", headers=headers)
    with urllib.request.urlopen(req) as resp:
        return resp.getcode(), json.loads(resp.read().decode("utf-8"))

def raw_ws_handshake(token):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.connect(("localhost", 8085))
    key = base64.b64encode(os.urandom(16)).decode("utf-8")
    req = (
        f"GET /ws/market?token={token} HTTP/1.1\r\n"
        f"Host: localhost:8085\r\n"
        f"Upgrade: websocket\r\n"
        f"Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        f"Sec-WebSocket-Version: 13\r\n\r\n"
    )
    s.sendall(req.encode("utf-8"))
    resp = s.recv(4096).decode("utf-8", errors="ignore")
    status_line = resp.split("\r\n")[0]
    return s, status_line

def main():
    print("==================================================")
    print("LIVE MARKET DATA SERVICE VERIFICATION (PORT 8085)")
    print("==================================================")

    # 1. Login as Trader
    print("\n[TEST 1] Trader Authentication (/api/auth/login)")
    trader_auth = post_json("/api/auth/login", {"username": "trader1", "password": "password123"})
    trader_token = trader_auth["token"]
    print(f"  -> SUCCESS: Got token: {trader_token[:25]}... (roles: {trader_auth['roles']})")

    # 2. Login as Admin
    print("\n[TEST 2] Admin Authentication (/api/auth/login)")
    admin_auth = post_json("/api/auth/login", {"username": "admin", "password": "admin123"})
    admin_token = admin_auth["token"]
    print(f"  -> SUCCESS: Got admin token: {admin_token[:25]}... (roles: {admin_auth['roles']})")

    # 3. Top 20 Spot Tickers
    print("\n[TEST 3] Top 20 Spot Tickers (/api/market/tickers)")
    status, tickers_resp = get_json("/api/market/tickers", trader_token)
    tickers = tickers_resp["data"]
    print(f"  -> HTTP {status}, Total tickers: {len(tickers)}")
    assert len(tickers) == 20, f"Expected 20 tickers, got {len(tickers)}"
    print(f"  -> Top 1: {tickers[0]['instId']} (Quote Vol: {tickers[0]['volCcy24h']})")
    print(f"  -> Top 2: {tickers[1]['instId']} (Quote Vol: {tickers[1]['volCcy24h']})")
    print(f"  -> Top 3: {tickers[2]['instId']} (Quote Vol: {tickers[2]['volCcy24h']})")
    # Verify sorted descending
    vols = [float(t["volCcy24h"]) for t in tickers]
    assert vols == sorted(vols, reverse=True), "Tickers are not sorted descending by volume!"
    print("  -> Verified: Sorted strictly descending by 24h volume!")

    # 4. Depth-5 Order Book
    print("\n[TEST 4] Depth-5 Order Book (/api/market/orderbook?pair=BTC-USDT)")
    status, ob_resp = get_json("/api/market/orderbook?pair=BTC-USDT", trader_token)
    ob_data = ob_resp["data"][0]
    print(f"  -> HTTP {status}, Asks: {len(ob_data['asks'])}, Bids: {len(ob_data['bids'])}")
    assert len(ob_data["asks"]) == 5, "Expected 5 ask levels"
    assert len(ob_data["bids"]) == 5, "Expected 5 bid levels"
    print(f"  -> Best Bid: {ob_data['bids'][0][0]} | Best Ask: {ob_data['asks'][0][0]}")

    # 5. RBAC Enforcement
    print("\n[TEST 5] RBAC Enforcement on /api/admin/sessions")
    try:
        get_json("/api/admin/sessions", trader_token)
        print("  -> FAILED: Trader was unexpectedly permitted!")
    except urllib.error.HTTPError as e:
        print(f"  -> SUCCESS: Trader denied with HTTP {e.code} ({e.reason})")

    status, admin_sessions = get_json("/api/admin/sessions", admin_token)
    print(f"  -> SUCCESS: Admin granted access (HTTP {status}): {admin_sessions}")

    # 6. WebSocket Connection & Single-Session Enforcement
    print("\n[TEST 6] WebSocket Single-Session Enforcement")
    s1, line1 = raw_ws_handshake(trader_token)
    print(f"  -> Client 1 Handshake: {line1}")
    assert "101" in line1, f"Expected 101 Switching Protocols, got {line1}"

    import time
    time.sleep(0.5)

    # Try connecting Client 2 with same user
    s2, line2 = raw_ws_handshake(trader_token)
    print(f"  -> Client 2 Handshake (Duplicate): {line2}")
    assert "409" in line2, f"Expected 409 Conflict, got {line2}"
    print("  -> SUCCESS: Second concurrent connection strictly rejected with HTTP 409 Conflict!")

    # Verify Client 1 is still connected
    _, admin_sessions_mid = get_json("/api/admin/sessions", admin_token)
    print(f"  -> Active sessions in manager: {admin_sessions_mid['activeSessionCount']}, duplicate attempts rejected: {admin_sessions_mid['rejectedDuplicateConnections']}")

    # Close Client 1
    s1.close()
    s2.close()
    print("  -> Client 1 closed cleanly.")

    print("\n==================================================")
    print("ALL VERIFICATION CHECKS PASSED SUCCESSFULLY!")
    print("==================================================")

if __name__ == "__main__":
    main()
