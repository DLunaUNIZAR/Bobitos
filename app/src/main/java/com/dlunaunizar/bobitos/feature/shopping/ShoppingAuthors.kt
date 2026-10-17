package com.dlunaunizar.bobitos.feature.shopping

// «Añadido por» y «Marcado por» solo informan cuando hay más gente en el espacio; sin el dato, se muestran.
internal fun showsAuthor(memberCount: Int?): Boolean = memberCount == null || memberCount > 1
