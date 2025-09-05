package com.aem.ai.pm.services;


import com.aem.ai.pm.dto.HoldingItem;
import com.aem.ai.pm.dto.PositionItem;

public interface InstrumentResolver {
    long resolveForHolding(HoldingItem h);   // returns instrument.id
    long resolveForPosition(PositionItem p); // "
}
