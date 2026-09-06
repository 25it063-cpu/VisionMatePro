package com.visionmate.pro.language

import com.visionmate.pro.model.AppLanguage
import com.visionmate.pro.model.ObjectType

object LocalizedObjectNames {

    fun getLocalizedName(objectType: ObjectType, language: AppLanguage): String {
        return when (language) {
            AppLanguage.ENGLISH -> when (objectType) {
                ObjectType.PERSON -> "person"
                ObjectType.STAIRCASE -> "staircase"
                ObjectType.TREE_BRANCH -> "low tree branch"
                ObjectType.POLE -> "pole"
                ObjectType.CHAIR -> "chair"
                ObjectType.VEHICLE -> "vehicle"
                ObjectType.DOOR -> "door"
                ObjectType.WALL -> "wall"
                ObjectType.WATER -> "water"
                ObjectType.ANIMAL -> "animal"
                ObjectType.STREET_OBJECT -> "street object"
                ObjectType.HOUSEHOLD_ITEM -> "item"
                ObjectType.OTHER -> "obstacle"
            }
            AppLanguage.TAMIL -> when (objectType) {
                ObjectType.PERSON -> "நபர்"
                ObjectType.STAIRCASE -> "படிக்கட்டு"
                ObjectType.TREE_BRANCH -> "தாழ்வான மரக்கிளை"
                ObjectType.POLE -> "கம்பம்"
                ObjectType.CHAIR -> "நாற்காலி"
                ObjectType.VEHICLE -> "வாகனம்"
                ObjectType.DOOR -> "கதவு"
                ObjectType.WALL -> "சுவர்"
                ObjectType.WATER -> "தண்ணீர்"
                ObjectType.ANIMAL -> "விலங்கு"
                ObjectType.STREET_OBJECT -> "சாலைப் பொருள்"
                ObjectType.HOUSEHOLD_ITEM -> "பொருள்"
                ObjectType.OTHER -> "தடை"
            }
            AppLanguage.HINDI -> when (objectType) {
                ObjectType.PERSON -> "व्यक्ति"
                ObjectType.STAIRCASE -> "सीढ़ी"
                ObjectType.TREE_BRANCH -> "पेड़ की नीची डाल"
                ObjectType.POLE -> "खंभा"
                ObjectType.CHAIR -> "कुर्सी"
                ObjectType.VEHICLE -> "वाहन"
                ObjectType.DOOR -> "दरवाजा"
                ObjectType.WALL -> "दीवार"
                ObjectType.WATER -> "पानी"
                ObjectType.ANIMAL -> "जानवर"
                ObjectType.STREET_OBJECT -> "सड़क की वस्तु"
                ObjectType.HOUSEHOLD_ITEM -> "सामान"
                ObjectType.OTHER -> "बाधा"
            }
            AppLanguage.TELUGU -> when (objectType) {
                ObjectType.PERSON -> "వ్యక్తి"
                ObjectType.STAIRCASE -> "మెట్లు"
                ObjectType.TREE_BRANCH -> "చెట్టు కొమ్మ"
                ObjectType.POLE -> "స్తంభం"
                ObjectType.CHAIR -> "కుర్చీ"
                ObjectType.VEHICLE -> "వాహనం"
                ObjectType.DOOR -> "ద్వారం"
                ObjectType.WALL -> "గోడ"
                ObjectType.WATER -> "నీరు"
                ObjectType.ANIMAL -> "జంతువు"
                ObjectType.STREET_OBJECT -> "వీధి వస్తువు"
                ObjectType.HOUSEHOLD_ITEM -> "వస్తువు"
                ObjectType.OTHER -> "అడ్డంకి"
            }
            AppLanguage.MALAYALAM -> when (objectType) {
                ObjectType.PERSON -> "വ്യക്തി"
                ObjectType.STAIRCASE -> "ഗോവണി"
                ObjectType.TREE_BRANCH -> "മരച്ചില്ല"
                ObjectType.POLE -> "തൂൺ"
                ObjectType.CHAIR -> "കസേర"
                ObjectType.VEHICLE -> "വാഹనం"
                ObjectType.DOOR -> "വാതിൽ"
                ObjectType.WALL -> "മതിൽ"
                ObjectType.WATER -> "വെള്ളം"
                ObjectType.ANIMAL -> "മൃഗം"
                ObjectType.STREET_OBJECT -> "തെരുവ് വസ്തു"
                ObjectType.HOUSEHOLD_ITEM -> "വസ്തു"
                ObjectType.OTHER -> "തടസ്സം"
            }
        }
    }
}
