package com.superfriend.superfriend.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class BatchSelectionRequest {
    private List<String> skillNames;
    private Boolean isSelected;
    private Long userId;
}
