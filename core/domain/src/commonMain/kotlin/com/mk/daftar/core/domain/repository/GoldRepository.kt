package com.mk.daftar.core.domain.repository

import com.mk.daftar.core.model.GoldForm
import com.mk.daftar.core.model.GoldHolding
import com.mk.daftar.core.model.GoldHoldingId
import com.mk.daftar.core.model.GoldKarat
import com.mk.daftar.core.model.GoldWeight

data class GoldHoldingInput(val karat: GoldKarat, val form: GoldForm, val weight: GoldWeight, val note: String?)

interface GoldRepository : RecordRepository<GoldHoldingId, GoldHolding, GoldHoldingInput>
