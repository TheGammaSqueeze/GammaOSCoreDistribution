package android.platform.test.rule

/** Checks if the class, or any of its superclasses, have [annotation]. */
fun <T> Class<T>?.hasAnnotation(annotation: Class<out Annotation>): Boolean =
    if (this == null) {
        false
    } else if (isAnnotationPresent(annotation)) {
        true
    } else {
        superclass.hasAnnotation(annotation)
    }
