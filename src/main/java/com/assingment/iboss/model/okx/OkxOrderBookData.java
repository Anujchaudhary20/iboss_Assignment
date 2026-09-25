package com.assingment.iboss.model.okx;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OkxOrderBookData implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * List of asks, where each entry is [price, size, numOrders] or [price, size, liquidatedOrders, orderCount]
     * formatted as Strings per OKX books5 specification.
     */
    private List<List<String>> asks;

    /**
     * List of bids, where each entry is [price, size, numOrders]
     * formatted as Strings per OKX books5 specification.
     */
    private List<List<String>> bids;

    private String ts;
    private Long seqId;
}
