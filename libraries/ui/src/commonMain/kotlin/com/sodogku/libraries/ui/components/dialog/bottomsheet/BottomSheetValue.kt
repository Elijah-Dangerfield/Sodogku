package com.sodogku.libraries.ui.components.dialog.bottomsheet

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue as MaterialSheetValue

@OptIn(ExperimentalMaterial3Api::class)
enum class BottomSheetValue  constructor(
    internal val materialValue: MaterialSheetValue,
) {
    @OptIn(ExperimentalMaterial3Api::class)
    Hidden(MaterialSheetValue.Hidden),
    Expanded(MaterialSheetValue.Expanded),
    PartiallyExpanded(MaterialSheetValue.PartiallyExpanded)
}

@OptIn(ExperimentalMaterial3Api::class)
internal fun MaterialSheetValue.toBottomSheetValue(): com.sodogku.libraries.ui.components.dialog.bottomsheet.BottomSheetValue =
    when (this) {
        MaterialSheetValue.Hidden -> com.sodogku.libraries.ui.components.dialog.bottomsheet.BottomSheetValue.Hidden
        MaterialSheetValue.Expanded -> com.sodogku.libraries.ui.components.dialog.bottomsheet.BottomSheetValue.Expanded
        MaterialSheetValue.PartiallyExpanded -> com.sodogku.libraries.ui.components.dialog.bottomsheet.BottomSheetValue.PartiallyExpanded
    }
