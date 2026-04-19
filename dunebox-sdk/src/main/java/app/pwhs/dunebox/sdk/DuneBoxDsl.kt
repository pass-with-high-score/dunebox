package app.pwhs.dunebox.sdk

/**
 * DSL marker to prevent scope leaking in nested DuneBox DSL blocks.
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class DuneBoxDsl
