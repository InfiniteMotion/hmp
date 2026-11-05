package com.hmp.data.database.myenum

import androidx.room.TypeConverter

enum class LabelName {
    ROCK,
    POP,
    JAZZ,
    CLASSICAL,
    HIPHOP,
    ELECTRONIC,
    FOLK,
    RNB,
    METAL,
    COUNTRY,
    BLUES,
    REGGAE,
    PUNK,
    FUNK,
    SOUL,
    INDIE,

    HAPPY,
    SAD,
    ENERGETIC,
    CALM,
    ROMANTIC,
    ANGRY,
    LONELY,
    UPLIFTING,
    MYSTERIOUS,
    DARK,
    MELANCHOLY,
    HOPEFUL,

    WORKOUT,
    SLEEP,
    PARTY,
    DRIVING,
    STUDY,
    RELAX,
    DINNER,
    MEDITATION,
    FOCUS,
    TRAVEL,
    MORNING,
    NIGHT,

    ENGLISH,
    CHINESE,
    JAPANESE,
    KOREAN,
    SPANISH,
    FRENCH,
    GERMAN,
    ITALIAN,
    ARABIC,
    HINDI,
    RUSSIAN,

    SIXTIES,
    SEVENTIES,
    EIGHTIES,
    NINETIES,
    TWO_THOUSANDS,
    TWENTY_TENS,
    TWENTY_TWENTIES,

    UNKNOWN,

    ;

    companion object {
        fun match(value: String): LabelName? = entries.find {
            it.name.equals(value, ignoreCase = true)
        }

        fun LabelName.getIconResourceName(): String = this.name.lowercase()
    }
}

enum class LabelCategory {
    GENRE,
    MOOD,
    SCENARIO,
    LANGUAGE,
    ERA,
}

class LabelConverters {
    @TypeConverter
    fun fromLabelCategory(category: LabelCategory): String = category.name

    @TypeConverter
    fun toLabelCategory(value: String): LabelCategory = LabelCategory.valueOf(value)

    @TypeConverter
    fun fromLabelName(label: LabelName): String = label.name

    @TypeConverter
    fun toLabelName(value: String): LabelName = LabelName.valueOf(value)
}
