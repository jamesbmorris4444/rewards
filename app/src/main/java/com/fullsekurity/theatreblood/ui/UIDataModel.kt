package com.fullsekurity.theatreblood.ui

import com.fullsekurity.theatreblood.R
import com.fullsekurity.theatreblood.activity.MainActivity

class UIDataModel {
    private var lightViewUIDataClass: UIDataClass? = null
    private var darkViewUIDataClass: UIDataClass? = null
    private fun createViewData(theme: MainActivity.UITheme): UIDataClass? {
        return when (theme) {
            MainActivity.UITheme.LIGHT, MainActivity.UITheme.NOT_ASSIGNED -> {
                if (lightViewUIDataClass == null) {
                    lightViewUIDataClass = UIDataClass(
                        R.drawable.lt_standard_modal_background,
                        R.drawable.ic_dashed_line,
                        320f,
                        "lightGray",
                        "lightGray",
                        48f,
                        24f,
                        "darkGray",
                        "mediumTextBold",
                        50f,
                        50f,
                        14f,
                        14f,
                        "darkGray",
                        "mediumText",
                        48f,
                        "blue",
                        "mediumText",
                        36f,
                        "darkGray",
                        "mediumText",
                        "blue",
                        "darkGray",
                        "mediumText",
                        12f,
                        "success",
                        "mediumText",
                        76f,
                        "darkGray",
                        "smallText",
                        "lightGray",
                        "smallText",
                        8f,
                        20f,
                        12f,
                        3f,
                        "darkGray",
                        "mediumText",

                        "mediumGray",
                        "black",
                        "subTitle",
                        "veryLightGray",
                        "blue",
                        R.drawable.lt_edit_text_background,

                        "mediumGray",
                        "black",
                        "subTitle",
                        "veryLightGray",
                        "blue",
                        R.drawable.lt_edit_text_background,

                        "white",
                        "subTitle",

                        "black",
                        "largeText",

                        "error",
                        "mainTitle"
                    )
                }
                lightViewUIDataClass
            }
            MainActivity.UITheme.DARK -> {
                if (darkViewUIDataClass == null) {
                    darkViewUIDataClass = UIDataClass(
                        R.drawable.dk_standard_modal_background,
                        R.drawable.ic_dashed_line,
                        320f,
                        "lightGray",
                        "lightGray",
                        48f,
                        24f,
                        "darkGray",
                        "mediumText",
                        50f,
                        50f,
                        14f,
                        14f,
                        "darkGray",
                        "mediumText",
                        48f,
                        "blue",
                        "mediumText",
                        36f,
                        "darkGray",
                        "mediumText",
                        "blue",
                        "darkGray",
                        "mediumText",
                        12f,
                        "success",
                        "mediumText",
                        76f,
                        "darkGray",
                        "smallText",
                        "lightGray",
                        "smallText",
                        8f,
                        20f,
                        12f,
                        3f,
                        "darkGray",
                        "mediumText",

                        "mediumGray",
                        "black",
                        "subTitle",
                        "veryLightGray",
                        "blue",
                        R.drawable.lt_edit_text_background,

                        "mediumGray",
                        "black",
                        "subTitle",
                        "veryLightGray",
                        "blue",
                        R.drawable.lt_edit_text_background,

                        "white",
                        "subTitle",

                        "black",
                        "largeText",

                        "error",
                        "mainTitle"
                    )
                }
                darkViewUIDataClass
            }
        }
    }

    fun loadData(theme: MainActivity.UITheme) : UIDataClass? {
        return createViewData(theme)
    }

}
