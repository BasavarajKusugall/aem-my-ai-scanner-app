package com.pm.services;


import com.pm.dto.HoldingItem;
import com.pm.dto.PositionItem;

public interface InstrumentResolver {
    long resolveForHolding(HoldingItem h);   // returns instrument.id
    long resolveForPosition(PositionItem p); // "
}
