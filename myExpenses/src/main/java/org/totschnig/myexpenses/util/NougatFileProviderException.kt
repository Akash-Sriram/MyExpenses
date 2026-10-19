package org.totschnig.myexpenses.util

class NougatFileProviderException(cause: Throwable? = null) :
    IllegalStateException("On Nougat, falling back to file uri won't work", cause)
