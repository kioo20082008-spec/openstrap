package org.openstrap.app.data

/**
 * The six home screen widgets (widgets spec 2). It lives in data, not widget, so the Moment lead rule ([momentLead])
 * can name which widgets are placed without data depending on the widget package. Energy came last, after the five
 * the spec started with.
 */
enum class WidgetKind { Moment, Sleep, Steps, Recovery, Start, Energy }
