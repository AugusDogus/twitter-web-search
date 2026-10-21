package local.newx.websearch

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.Reference

private const val VIEW = "Landroid/view/View;"
private const val FUNCTION = "Lkotlin/jvm/functions/Function1;"
private const val MENU_ADD = "Landroid/view/Menu;->add(IIII)Landroid/view/MenuItem;"

internal fun <T> exactlyOne(label: String, candidates: List<T>): T {
    if (candidates.size != 1) {
        throw PatchException("Web search: expected one $label, found ${candidates.size}: $candidates. " +
            "This X build has a different selection implementation; use a supported APK.")
    }
    return candidates.single()
}

private inline fun <reified T : Reference> Instruction.reference(): T? =
    (this as? ReferenceInstruction)?.reference as? T

private fun Method.code(): List<Instruction> = implementation?.instructions?.toList()
    ?: throw PatchException("Web search: method has no bytecode: $this")

internal data class CallbackHook(val constructor: Method, val storeIndex: Int, val view: Int, val callback: Int)
internal data class MenuHook(val method: Method, val callIndex: Int, val registers: List<Int>, val itemId: Int)

/** Only follow adjacent constants, never cross a branch, call or register overwrite. */
private fun literalBefore(code: List<Instruction>, end: Int, register: Int): Int {
    for (index in end - 1 downTo 0) {
        val instruction = code[index]
        val constant = instruction as? NarrowLiteralInstruction ?: break
        if ((instruction as? OneRegisterInstruction)?.registerA == register) return constant.narrowLiteral
    }
    throw PatchException("Web search: cannot prove menu item ID in v$register before instruction $end")
}

/** Anchor the exact title resource in a framework selection menu, independent of R8 names. */
internal fun resolveMenu(classes: List<ClassDef>, titleId: Int): MenuHook {
    val method = exactlyOne("selection menu creator", classes.filter { cls ->
        cls.superclass == "Landroid/view/ActionMode\$Callback2;"
    }.flatMap { it.methods.toList() }.filter { method ->
        method.name == "onCreateActionMode" && method.returnType == "Z" &&
            method.parameterTypes.map(CharSequence::toString) == listOf("Landroid/view/ActionMode;", "Landroid/view/Menu;") &&
            method.code().any { (it as? NarrowLiteralInstruction)?.narrowLiteral == titleId }
    })
    val code = method.code()
    val titleIndex = exactlyOne("Ask Grok title constant in $method", code.indices.filter {
        (code[it] as? NarrowLiteralInstruction)?.narrowLiteral == titleId
    })
    val titleRegister = (code[titleIndex] as? OneRegisterInstruction)?.registerA
        ?: throw PatchException("Web search: menu title has no destination register in $method")
    var callIndex = titleIndex + 1
    while (callIndex < code.size && code[callIndex] is NarrowLiteralInstruction) {
        val instruction = code[callIndex] as? OneRegisterInstruction
        if (instruction == null || instruction.registerA == titleRegister) break
        callIndex++
    }
    val call = code.getOrNull(callIndex)
    val args = call as? FiveRegisterInstruction
    if (call?.opcode != Opcode.INVOKE_INTERFACE || args == null || args.registerCount != 5 ||
        args.registerG != titleRegister || call.reference<MethodReference>().toString() != MENU_ADD) {
        throw PatchException("Web search: Ask Grok resource does not directly feed Menu.add in $method")
    }
    return MenuHook(method, callIndex, listOf(args.registerC, args.registerD, args.registerE, args.registerF),
        literalBefore(code, callIndex, args.registerE))
}

/** Follow the resolved menu item's switch arm to its callback field, then its constructor input. */
internal fun resolveCallback(classes: List<ClassDef>, menu: MenuHook): CallbackHook {
    val owner = exactlyOne("selection menu class", classes.filter { it.type == menu.method.definingClass })
    val click = exactlyOne("selection click handler", owner.methods.filter {
        it.name == "onActionItemClicked" && it.returnType == "Z" &&
            it.parameterTypes.map(CharSequence::toString) == listOf("Landroid/view/ActionMode;", "Landroid/view/MenuItem;")
    })
    val code = click.code()
    val offsets = code.runningFold(0) { offset, instruction -> offset + instruction.codeUnits }.dropLast(1)
    fun atOffset(offset: Int) = exactlyOne("instruction at code offset $offset in $click", offsets.indices.filter { offsets[it] == offset })
    val itemCall = exactlyOne("MenuItem.getItemId call in $click", code.indices.filter {
        code[it].reference<MethodReference>().toString() == "Landroid/view/MenuItem;->getItemId()I"
    })
    val result = code.getOrNull(itemCall + 1) as? OneRegisterInstruction
    if (result == null || code[itemCall + 1].opcode != Opcode.MOVE_RESULT) {
        throw PatchException("Web search: menu item ID result is missing in $click")
    }
    val switchIndex = exactlyOne("menu item switch in $click", code.indices.filter {
        code[it].opcode in setOf(Opcode.SPARSE_SWITCH, Opcode.PACKED_SWITCH) &&
            (code[it] as? OneRegisterInstruction)?.registerA == result.registerA
    })
    if (switchIndex <= itemCall + 1 || code.subList(itemCall + 2, switchIndex).any {
            it is OffsetInstruction || (it.opcode.setsRegister() && it is OneRegisterInstruction && it.registerA == result.registerA)
        }) throw PatchException("Web search: menu item ID is overwritten before its switch in $click")
    val switch = code[switchIndex] as? OffsetInstruction
        ?: throw PatchException("Web search: menu switch has no payload offset")
    val payload = code[atOffset(offsets[switchIndex] + switch.codeOffset)] as? SwitchPayload
        ?: throw PatchException("Web search: menu switch payload is invalid")
    val arm = exactlyOne("Ask Grok switch arm", payload.switchElements.filter { it.key == menu.itemId })
    val armIndex = atOffset(offsets[switchIndex] + arm.offset)
    val read = code[armIndex]
    val field = read.reference<FieldReference>()
    val readRegisters = read as? TwoRegisterInstruction
    val store = code.getOrNull(armIndex + 1)
    val storeRegisters = store as? TwoRegisterInstruction
    val pending = store?.reference<FieldReference>()
    if (read.opcode != Opcode.IGET_OBJECT || field == null || field.type != FUNCTION || readRegisters == null ||
        store?.opcode != Opcode.IPUT_OBJECT || storeRegisters == null || pending == null ||
        pending.type != FUNCTION || pending.definingClass != field.definingClass || pending == field ||
        readRegisters.registerA != storeRegisters.registerA || readRegisters.registerB != storeRegisters.registerB ||
        owner.fields.none { it.type == field.definingClass }) {
        throw PatchException("Web search: Ask Grok arm no longer selects a toolbar callback in $click")
    }
    val toolbar = exactlyOne("selection toolbar", classes.filter { it.type == field.definingClass })
    val constructor = exactlyOne("selection toolbar constructor", toolbar.methods.filter {
        it.name == "<init>" && it.parameterTypes.map(CharSequence::toString) == listOf(VIEW, FUNCTION, FUNCTION)
    })
    val body = constructor.code()
    val frame = constructor.implementation?.registerCount
        ?: throw PatchException("Web search: constructor has no register frame")
    val receiver = frame - 4
    val view = receiver + 1
    val storeIndex = exactlyOne("Ask Grok field assignment", body.indices.filter {
        body[it].opcode == Opcode.IPUT_OBJECT && body[it].reference<FieldReference>() == field
    })
    val operands = body[storeIndex] as? TwoRegisterInstruction
        ?: throw PatchException("Web search: callback assignment has invalid registers")
    val callback = operands.registerA
    if (operands.registerB != receiver || callback !in receiver + 2..receiver + 3 || callback > 255 ||
        body.take(storeIndex).any {
            it is OffsetInstruction || (it.opcode.setsRegister() && it is OneRegisterInstruction &&
                it.registerA in setOf(view, callback))
        } || body.drop(storeIndex + 1).map { it.opcode } != listOf(Opcode.RETURN_VOID)) {
        throw PatchException("Web search: selection constructor is no longer a straight-line field initializer: $constructor")
    }
    return CallbackHook(constructor, storeIndex, view, callback)
}
