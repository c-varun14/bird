package com.example.projectbird.data.local.converter

import androidx.room.TypeConverter
import com.example.projectbird.data.local.entity.EnvironmentLabel
import com.example.projectbird.data.local.entity.RetentionReason
import com.example.projectbird.data.local.entity.Visibility

class DbConverters {

    @TypeConverter
    fun fromEnvironmentLabel(value: EnvironmentLabel?): String? = value?.name

    @TypeConverter
    fun toEnvironmentLabel(value: String?): EnvironmentLabel? =
        value?.let(EnvironmentLabel::valueOf)

    @TypeConverter
    fun fromRetentionReason(value: RetentionReason?): String? = value?.name

    @TypeConverter
    fun toRetentionReason(value: String?): RetentionReason? =
        value?.let(RetentionReason::valueOf)

    @TypeConverter
    fun fromVisibility(value: Visibility?): String? = value?.name

    @TypeConverter
    fun toVisibility(value: String?): Visibility? =
        value?.let(Visibility::valueOf)
}
