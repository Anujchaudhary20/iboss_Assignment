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
public class OkxResponse<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    @Builder.Default
    private String code = "0";
    @Builder.Default
    private String msg = "";
    private List<T> data;
}
