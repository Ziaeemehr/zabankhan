@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ziaee.zabankhan.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIApplication
import platform.UIKit.UIPencilInteraction
import platform.UIKit.UIPencilInteractionDelegateProtocol
import platform.UIKit.UIPencilPreferredActionIgnore
import platform.UIKit.UIPencilPreferredActionSwitchEraser
import platform.UIKit.UIPencilPreferredActionSwitchPrevious
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.darwin.NSObject

private class PencilInteractionHolder {
    var interaction: UIPencilInteraction? = null
    var view: UIView? = null
    var onToggle: () -> Unit = {}
    val delegate = PencilInteractionDelegate(this)
}

private class PencilInteractionDelegate(private val holder: PencilInteractionHolder) : NSObject(), UIPencilInteractionDelegateProtocol {
    override fun pencilInteractionDidTap(interaction: UIPencilInteraction) {
        when (UIPencilInteraction.preferredTapAction) {
            UIPencilPreferredActionSwitchEraser, UIPencilPreferredActionSwitchPrevious -> holder.onToggle()
            UIPencilPreferredActionIgnore -> Unit
        }
    }
}

@Composable
actual fun PencilDoubleTapEffect(enabled: Boolean, onToggle: () -> Unit) {
    val latestOnToggle = rememberUpdatedState(onToggle)
    val holder = remember { PencilInteractionHolder() }
    DisposableEffect(enabled) {
        if (enabled) {
            val window = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
                .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
                ?.windows?.filterIsInstance<UIWindow>()?.firstOrNull { it.isKeyWindow() }
            val view = window?.rootViewController?.view
            if (view != null) {
                holder.onToggle = { latestOnToggle.value() }
                val interaction = UIPencilInteraction()
                interaction.delegate = holder.delegate
                view.performSelector(NSSelectorFromString("addInteraction:"), interaction)
                holder.view = view
                holder.interaction = interaction
            }
        }
        onDispose {
            holder.interaction?.let { interaction ->
                holder.view?.performSelector(NSSelectorFromString("removeInteraction:"), interaction)
            }
            holder.interaction = null
            holder.view = null
            holder.onToggle = {}
        }
    }
}
