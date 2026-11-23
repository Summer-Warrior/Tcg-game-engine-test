package com.example

import com.example.data.BuiltInTcgCatalog
import com.example.data.CardEffectType
import com.example.data.GameEngine
import com.example.data.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    @Test
    fun `advancePhase cycles through DRAW STANDBY MAIN COMBAT END and increments turn on wrap`() {
        val engine = GameEngine("EDOPRO_BASE_MR5")
        // Initial phase is MAIN on Turn 1
        assertEquals(TurnPhase.MAIN, engine.state.value.currentPhase)
        assertEquals(1L, engine.state.value.turnNumber)

        // MAIN -> COMBAT
        engine.advancePhase()
        assertEquals(TurnPhase.COMBAT, engine.state.value.currentPhase)

        // COMBAT -> END
        engine.advancePhase()
        assertEquals(TurnPhase.END, engine.state.value.currentPhase)

        // END -> DRAW (Turn 2)
        engine.advancePhase()
        assertEquals(TurnPhase.DRAW, engine.state.value.currentPhase)
        assertEquals(2L, engine.state.value.turnNumber)

        // DRAW -> STANDBY
        engine.advancePhase()
        assertEquals(TurnPhase.STANDBY, engine.state.value.currentPhase)

        // STANDBY -> MAIN
        engine.advancePhase()
        assertEquals(TurnPhase.MAIN, engine.state.value.currentPhase)
    }

    @Test
    fun `queueCardEffect and resolveNextEffect resolve in LIFO stack order and apply burn heal and counter`() {
        val engine = GameEngine("EDOPRO_BASE_MR5")
        val card = BuiltInTcgCatalog.cardsForVariant("EDOPRO_BASE_MR5").first()
        val initialOppLife = engine.state.value.opponentLife
        val initialHostLife = engine.state.value.hostLife

        // Queue Chain Link 1: Burn
        engine.queueCardEffect(card = card, overrideEffectType = CardEffectType.DIRECT_DAMAGE_BURN)
        // Queue Chain Link 2: Heal
        engine.queueCardEffect(card = card, overrideEffectType = CardEffectType.LIFE_RESTORE_HEAL)

        assertEquals(2, engine.state.value.effectStack.size)
        assertEquals(2, engine.state.value.effectStack.last().chainLinkNumber)

        // Resolve Top (Chain Link 2: Heal)
        engine.resolveNextEffect()
        assertEquals(1, engine.state.value.effectStack.size)
        assertTrue(engine.state.value.hostLife > initialHostLife)

        // Resolve Remaining (Chain Link 1: Burn)
        engine.resolveNextEffect()
        assertTrue(engine.state.value.effectStack.isEmpty())
        assertTrue(engine.state.value.opponentLife < initialOppLife)
        assertEquals(2, engine.state.value.resolvedEffectsHistory.size)
    }

    @Test
    fun `negateAndCounter removes underlying chain link from stack`() {
        val engine = GameEngine("FORGE_MTG_BASE")
        val card = BuiltInTcgCatalog.cardsForVariant("FORGE_MTG_BASE").first()
        val initialOppLife = engine.state.value.opponentLife

        // Chain Link 1: Direct burn
        engine.queueCardEffect(card = card, overrideEffectType = CardEffectType.DIRECT_DAMAGE_BURN)
        // Chain Link 2: Negate & Counter
        engine.queueCardEffect(card = card, overrideEffectType = CardEffectType.NEGATE_AND_COUNTER)

        assertEquals(2, engine.state.value.effectStack.size)

        // Resolving Chain Link 2 should negate and remove Chain Link 1 from the stack
        engine.resolveNextEffect()
        assertTrue(engine.state.value.effectStack.isEmpty())
        assertEquals(initialOppLife, engine.state.value.opponentLife)
    }
}
