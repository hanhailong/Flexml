package com.guet.flexbox.build

import com.guet.flexbox.Orientation

object Scroller : Declaration(Common) {
    override val attributeInfoSet: AttributeInfoSet by create {
        bool("scrollBarEnable")
        enum("orientation", mapOf(
                "vertical" to Orientation.VERTICAL,
                "horizontal" to Orientation.HORIZONTAL
        ))
        bool("fillViewport")
    }
}