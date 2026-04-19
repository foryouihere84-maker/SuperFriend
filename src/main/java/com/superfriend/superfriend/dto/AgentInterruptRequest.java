package com.superfriend.superfriend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentInterruptRequest {
    private String mode;
    private String context;
}
