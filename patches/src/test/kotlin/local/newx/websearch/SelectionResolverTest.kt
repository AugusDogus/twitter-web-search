package local.newx.websearch

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.smali.SmaliTestUtils.compileSmali
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Guards R8's removal of parameter names and selection of the wrong Function1 (Mute). */
class SelectionResolverTest {
    private val titleId = 0x7f010002

    private fun fixture(namedParameters: Boolean = false, grokParameter: Int = 3) = listOf(
        compileSmali("""
            .class public Lfixture/Toolbar;
            .super Ljava/lang/Object;
            .field public view:Landroid/view/View;
            .field public mute:Lkotlin/jvm/functions/Function1;
            .field public grok:Lkotlin/jvm/functions/Function1;
            .field public pending:Lkotlin/jvm/functions/Function1;
            .method public constructor <init>(Landroid/view/View;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;)V
                .registers 5
                ${if (namedParameters) """
                    const-string v0, "onAskGrok"
                    invoke-static {p$grokParameter, v0}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V
                """ else "invoke-virtual {p$grokParameter}, Ljava/lang/Object;->getClass()Ljava/lang/Class;"}
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                iput-object p1, p0, Lfixture/Toolbar;->view:Landroid/view/View;
                iput-object p${5 - grokParameter}, p0, Lfixture/Toolbar;->mute:Lkotlin/jvm/functions/Function1;
                iput-object p$grokParameter, p0, Lfixture/Toolbar;->grok:Lkotlin/jvm/functions/Function1;
                return-void
            .end method
        """.trimIndent()),
        compileSmali("""
            .class public Lfixture/SelectionMenu;
            .super Landroid/view/ActionMode${'$'}Callback2;
            .field public toolbar:Lfixture/Toolbar;
            .method public onCreateActionMode(Landroid/view/ActionMode;Landroid/view/Menu;)Z
                .registers 7
                const/4 v0, 0x0
                const v1, 0x12345
                const v2, 0x7f010002
                const/4 v3, 0x1
                invoke-interface {p2, v0, v1, v3, v2}, Landroid/view/Menu;->add(IIII)Landroid/view/MenuItem;
                return v3
            .end method
            .method public onActionItemClicked(Landroid/view/ActionMode;Landroid/view/MenuItem;)Z
                .registers 5
                invoke-interface {p2}, Landroid/view/MenuItem;->getItemId()I
                move-result v1
                iget-object v0, p0, Lfixture/SelectionMenu;->toolbar:Lfixture/Toolbar;
                sparse-switch v1, :items
                const/4 v1, 0x0
                return v1
                :grok
                iget-object v1, v0, Lfixture/Toolbar;->grok:Lkotlin/jvm/functions/Function1;
                iput-object v1, v0, Lfixture/Toolbar;->pending:Lkotlin/jvm/functions/Function1;
                const/4 v1, 0x1
                return v1
                :mute
                iget-object v1, v0, Lfixture/Toolbar;->mute:Lkotlin/jvm/functions/Function1;
                iput-object v1, v0, Lfixture/Toolbar;->pending:Lkotlin/jvm/functions/Function1;
                const/4 v1, 0x1
                return v1
                :items
                .sparse-switch
                    0x12344 -> :mute
                    0x12345 -> :grok
                .end sparse-switch
            .end method
        """.trimIndent()),
    )

    @Test fun `finds callback without parameter names and with reordered Function1 arguments`() {
        for (named in listOf(false, true)) for (parameter in listOf(2, 3)) {
            val classes = fixture(named, parameter)
            val menu = resolveMenu(classes, titleId)
            val callback = resolveCallback(classes, menu)
            assertEquals(parameter + 1, callback.callback)
            assertEquals(2, callback.view)
            assertEquals(0x12345, menu.itemId)
        }
    }

    @Test fun `rejects absent and ambiguous menus`() {
        val classes = fixture()
        assertFailsWith<PatchException> { resolveMenu(classes, titleId + 1) }
        assertFailsWith<PatchException> { resolveMenu(classes + classes, titleId) }
    }

    @Test fun `rejects a missing switch arm rather than redirecting another action`() {
        val classes = fixture()
        val menu = resolveMenu(classes, titleId).copy(itemId = 0x999)
        assertFailsWith<PatchException> { resolveCallback(classes, menu) }
    }
}
