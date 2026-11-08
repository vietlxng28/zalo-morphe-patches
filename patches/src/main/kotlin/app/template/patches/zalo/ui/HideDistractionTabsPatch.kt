package app.template.patches.zalo.ui

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.template.patches.shared.Constants.COMPATIBILITY_ZALO
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

/**
 * Hides Discovery (Khám phá) and Timeline/Diary (Nhật ký) tabs from the main bottom navigation bar.
 *
 * This removes distractions from short-form videos (Zalo Video), mini-apps, stories, and feed,
 * turning Zalo into a clean, distraction-free messaging and calls client.
 */
@Suppress("unused")
val hideDistractionTabsPatch = bytecodePatch(
    name = "Hide distraction tabs",
    description = "Hides the Discovery (Khám phá) and Timeline (Nhật ký) tabs from the bottom navigation bar to reduce distractions.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_ZALO)

    execute {
        val matches = MainTabBarSetupFingerprint.matchAll(1..1)
        val match = matches.single()
        val method = match.method

        val instructions = method.implementation?.instructions?.toList()
            ?: error("MainTabBarSetup method has no implementation")

        // 1. Force discovery tab flag (l) to false:
        // Replace `sget-boolean v1, Lxa1/i;->b:Z` with `const/4 v1, 0x0`
        val discoverySgetIndex = instructions.indexOfFirst { insn ->
            (insn.opcode == Opcode.SGET_BOOLEAN || insn.opcode == Opcode.SGET) &&
                (insn as? ReferenceInstruction)?.reference is FieldReference &&
                ((insn as ReferenceInstruction).reference as FieldReference).name == "b" &&
                ((insn as ReferenceInstruction).reference as FieldReference).definingClass == "Lxa1/i;"
        }
        check(discoverySgetIndex != -1) { "Could not find sget-boolean for Lxa1/i;->b:Z" }

        val sgetReg = (instructions[discoverySgetIndex] as OneRegisterInstruction).registerA
        method.replaceInstruction(discoverySgetIndex, "const/4 v$sgetReg, 0x0")

        // 2. Force timeline gate to false:
        // In kj1/v.p(), timeline presence begins with:
        // `sget-boolean v1, Lr00/a;->g:Z` followed by `if-eqz v1, :cond_2e`.
        // Replacing `sget-boolean v1, Lr00/a;->g:Z` with `const/4 v1, 0x0` ensures the branch
        // always jumps to `:cond_2e`, assigning k = 0x0 (false).
        val timelineGateIndex = instructions.indexOfFirst { insn ->
            (insn.opcode == Opcode.SGET_BOOLEAN || insn.opcode == Opcode.SGET) &&
                (insn as? ReferenceInstruction)?.reference is FieldReference &&
                ((insn as ReferenceInstruction).reference as FieldReference).name == "g" &&
                ((insn as ReferenceInstruction).reference as FieldReference).definingClass == "Lr00/a;"
        }
        check(timelineGateIndex != -1) { "Could not find sget-boolean for Lr00/a;->g:Z" }

        val gateReg = (instructions[timelineGateIndex] as OneRegisterInstruction).registerA
        method.replaceInstruction(timelineGateIndex, "const/4 v$gateReg, 0x0")

        // 3. Dual-lock: directly suppress the if-check when inserting TIMELINE tab into ArrayList:
        // `iget-boolean v5, p0, Lkj1/v;->k:Z` right before `if-eqz v5, :cond_56`.
        // Overwriting this iget with `const/4 v5, 0x0` guarantees the tab is NEVER added under any condition.
        val timelineIgetIndex = instructions.indexOfFirst { insn ->
            (insn.opcode == Opcode.IGET_BOOLEAN || insn.opcode == Opcode.IGET) &&
                (insn as? ReferenceInstruction)?.reference is FieldReference &&
                ((insn as ReferenceInstruction).reference as FieldReference).name == "k" &&
                ((insn as ReferenceInstruction).reference as FieldReference).definingClass == "Lkj1/v;"
        }
        if (timelineIgetIndex != -1) {
            val igetReg = (instructions[timelineIgetIndex] as OneRegisterInstruction).registerA
            method.replaceInstruction(timelineIgetIndex, "const/4 v$igetReg, 0x0")
        }

        // Dual-lock for DISCOVERY tab as well:
        val discoveryIgetIndex = instructions.indexOfFirst { insn ->
            (insn.opcode == Opcode.IGET_BOOLEAN || insn.opcode == Opcode.IGET) &&
                (insn as? ReferenceInstruction)?.reference is FieldReference &&
                ((insn as ReferenceInstruction).reference as FieldReference).name == "l" &&
                ((insn as ReferenceInstruction).reference as FieldReference).definingClass == "Lkj1/v;"
        }
        if (discoveryIgetIndex != -1) {
            val igetReg = (instructions[discoveryIgetIndex] as OneRegisterInstruction).registerA
            method.replaceInstruction(discoveryIgetIndex, "const/4 v$igetReg, 0x0")
        }
    }
}

private object MainTabBarSetupFingerprint : Fingerprint(
    definingClass = "Lkj1/v;",
    name = "p",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        string("ENABLE_GROUP_TAB"),
        string("ENABLE_TIMELINE_TAB"),
    ),
)
