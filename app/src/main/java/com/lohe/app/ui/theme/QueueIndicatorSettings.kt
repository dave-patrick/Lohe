package com.lohe.app.ui.theme

enum class QueueIndicatorStyle(val label: String) {
    DOT("Dot"), RING("Ring"), QUEUE_ICON("Queue icon"),
}

enum class QueueIndicatorColor(val label: String) {
    BLUE("Blue"), TEAL("Teal"), AMBER("Amber"),
}

enum class QueueIndicatorSize(val label: String, val dp: Int) {
    SMALL("Small", 8), MEDIUM("Medium", 12), LARGE("Large", 16),
}

data class QueueIndicatorSettings(
    val enabled: Boolean = true,
    val style: QueueIndicatorStyle = QueueIndicatorStyle.DOT,
    val color: QueueIndicatorColor = QueueIndicatorColor.BLUE,
    val size: QueueIndicatorSize = QueueIndicatorSize.SMALL,
) {
    companion object {
        fun fromStored(enabled: Boolean?, style: String?, color: String?, size: String?) =
            QueueIndicatorSettings(
                enabled = enabled ?: true,
                style = QueueIndicatorStyle.entries.firstOrNull { it.name == style } ?: QueueIndicatorStyle.DOT,
                color = QueueIndicatorColor.entries.firstOrNull { it.name == color } ?: QueueIndicatorColor.BLUE,
                size = QueueIndicatorSize.entries.firstOrNull { it.name == size } ?: QueueIndicatorSize.SMALL,
            )
    }
}
