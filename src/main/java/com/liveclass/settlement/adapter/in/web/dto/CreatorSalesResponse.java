package com.liveclass.settlement.adapter.in.web.dto;

import java.util.List;

public record CreatorSalesResponse(String creatorId, List<SaleItem> sales) {
}
