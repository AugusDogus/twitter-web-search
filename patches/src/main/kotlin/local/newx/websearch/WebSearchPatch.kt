package local.newx.websearch

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.rawResourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.reandroid.arsc.chunk.TableBlock

private const val HELPER = "Llocal/newx/websearch/WebSearch;"

@Suppress("unused")
val webSearchPatch = bytecodePatch(
    name = "NewX: Web search selected text",
    description = "Replaces Ask Grok in post text selection with Android's Web search action.",
) {
    compatibleWith(Compatibility(
        name = "X", packageName = "com.twitter.android", apkFileType = ApkFileType.APKM,
        targets = listOf("12.25.2-prod.01", "12.35.0-prod.01", "12.36.1-prod.01").map { AppTarget(version = it) },
    ))
    var titleId: Int? = null
    // Use the resource API available in the user's Morphe Manager 1.29 (patcher 1.12).
    dependsOn(rawResourcePatch {
        execute {
            titleId = null
            get("resources.arsc").inputStream().use { stream ->
                val table = TableBlock.load(stream)
                val entries = table.getEntries("com.twitter.android", "string", "text_selection_action_ask_grok")
                titleId = exactlyOne("Ask Grok string resource", entries.asSequence().map { it.resourceId }.distinct().toList())
            }
        }
    })
    extendWith("extensions/web-search.dex")
    execute {
        val classes = mutableListOf<ClassDef>()
        classDefForEach { classes.add(it) }
        val menu = resolveMenu(classes, titleId ?: throw PatchException("Web search: resource mapping did not run"))
        val callback = resolveCallback(classes, menu)
        // Resolve every required target before changing either half of the feature.
        val constructor = exactlyOne("mutable selection constructor",
            mutableClassDefBy(callback.constructor.definingClass).methods.filter { it == callback.constructor })
        val menuMethod = exactlyOne("mutable selection menu",
            mutableClassDefBy(menu.method.definingClass).methods.filter { it == menu.method })

        constructor.addInstructions(callback.storeIndex, listOf(
            BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE, callback.view, 1,
                ImmutableMethodReference(HELPER, "create", listOf("Landroid/view/View;"), "Lkotlin/jvm/functions/Function1;")),
            BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, callback.callback),
        ))
        val (menuRegister, group, item, order) = menu.registers
        menuMethod.replaceInstruction(menu.callIndex, BuilderInstruction35c(
            Opcode.INVOKE_STATIC, 4, menuRegister, group, item, order, 0,
            ImmutableMethodReference(HELPER, "addMenuItem", listOf("Landroid/view/Menu;", "I", "I", "I"), "Landroid/view/MenuItem;"),
        ))
        println("Web search: replaced callback in ${callback.constructor} and label in ${menu.method}")
    }
}
