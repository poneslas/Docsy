package com.chethan616.clearpdf.update

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors

/**
 * «Что нового» в Доках: экран ClearPDF (WhatsNewScreen) с нашими пунктами. Показывается один раз
 * после обновления на новую минорную или мажорную версию (1.3 → 1.4, 1.x → 2.0); патчи (1.3 → 1.3.1)
 * проходят молча. Пустой список — экран не показывается вовсе.
 *
 * Перед каждым минорным выпуском: 3–5 пунктов о том, что изменилось с прошлой минорной версии.
 * Заголовок — что изменилось для человека, описание — одна короткая фраза. Строки — в
 * app/src/docsy/res/values*\/strings_whatsnew.xml на всех семи языках. Пример:
 *
 *     DocsyWhatsNewItem(Icons.Rounded.Draw, LiquidGlassColors.Blue,
 *         R.string.docsy_new_initials_title, R.string.docsy_new_initials_desc)
 */
object DocsyWhatsNew {
    val items: List<DocsyWhatsNewItem> = listOf(
        DocsyWhatsNewItem(Icons.Rounded.IosShare, LiquidGlassColors.Blue, R.string.docsy_new_share_tap_title, R.string.docsy_new_share_tap_desc),
        DocsyWhatsNewItem(Icons.Rounded.MoreVert, LiquidGlassColors.Teal, R.string.docsy_new_scrub_title, R.string.docsy_new_scrub_desc),
    )
}

class DocsyWhatsNewItem(val icon: ImageVector, val accent: Color, val title: Int, val desc: Int)
