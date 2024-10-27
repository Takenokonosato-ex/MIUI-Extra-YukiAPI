package moe.chenxy.miuiextra.hooker.entity.systemui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import android.view.SurfaceControl
import android.view.View
import android.view.Window
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.utils.ChenUtils.Companion.lerp

object GlobalActionOptimizer : YukiBaseHooker() {
    override fun onHook() {
        fun blurRadiusOfRatio(f: Float): Int {
            if (f == 0.0f) {
                return 0;
            }
            return lerp(1.0f, 100.0f, f).toInt()
        }

        fun blurAndScale(view: View, ratio: Float, context: Context) {
            val viewRootImpl = XposedHelpers.callMethod(view, "getViewRootImpl")
            if (viewRootImpl == null) return

            val surfaceControl = XposedHelpers.callMethod(
                viewRootImpl,
                "getSurfaceControl"
            ) as SurfaceControl
            if (surfaceControl.isValid) {
                val transaction = SurfaceControl.Transaction()
                try {
                    val blurDisabled = Settings.Global.getInt(context.contentResolver, "disable_window_blurs", 0) != 0
                    if (!blurDisabled)
                    {
                        val radius = blurRadiusOfRatio(ratio)
                        XposedHelpers.callMethod(
                            transaction,
                            "setBackgroundBlurRadius",
                            surfaceControl,
                            radius
                        )
                        XposedHelpers.callMethod(
                            transaction,
                            "setBlurScaleRatio",
                            surfaceControl,
                            ratio / (10 - 3)
                        )
                        transaction.apply()
                        transaction.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        "com.android.systemui.miui.globalactions.SliderView".toClass().method {
            name = "startBlurAnim"
            paramCount = 3
        }.hook {
            replaceUnit {
                val from = this.args[0] as Float
                val to = this.args[1] as Float
                val duration = this.args[2] as Int
                val sliderView = this.instance as View
                val win = XposedHelpers.getObjectField(sliderView, "mWindow") as Window
                val context = XposedHelpers.getObjectField(sliderView, "mContext") as Context

                val animator = ValueAnimator.ofFloat(from, to)
                animator.addUpdateListener {
                    val value = it.animatedValue as Float
                    win.setBackgroundDrawable(ColorDrawable(Color.argb(blurRadiusOfRatio(value), 0, 0, 0)))
                    blurAndScale(sliderView.rootView, value, context)
                    sliderView.layoutParams.height = XposedHelpers.getIntField(sliderView, "mSliderHeight")
                }
                // 300 - Open / 200 - Close
                animator.duration = if (duration == 300) 500 else duration.toLong()
                if (duration == 300)
                    animator.interpolator = PathInterpolator(0f, 0.38f, 0.5f, 1f)
                animator.start()
            }
        }
    }
}