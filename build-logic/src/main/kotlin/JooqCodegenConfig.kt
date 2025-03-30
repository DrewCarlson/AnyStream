import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.Logging


fun Configuration.anystreamConfig(dbUrl: String) {
    logging = Logging.DEBUG
    jdbc.apply {
        driver = "org.sqlite.JDBC"
        url = dbUrl
    }
    generator.apply {
        name = "Generator"
        strategy.name = "JooqStrategy"
        target.packageName = "anystream.db"
        generate.apply {
            isDaos = false
            isRecords = true
            isKotlinNotNullRecordAttributes = true
            isKotlinNotNullInterfaceAttributes = true
            isJavaTimeTypes = false
            // Pojos as simple data classes
            isSerializablePojos = false
            isImmutablePojos = true
            isPojosToString = false
            isPojosEqualsAndHashCode = false
            isPojosAsKotlinDataClasses = true
            isKotlinNotNullPojoAttributes = true
        }
        database.apply {
            name = "org.jooq.meta.sqlite.SQLiteDatabase"
            excludes = listOf(
                // Exclude flyway migration tables
                "flyway_.*",
                // Exclude search meta tables
                "searchable_content_.*",
            ).joinToString("|")
            forcedTypes.addAll(forcedTypes())
        }
    }
}

fun forcedType(
    userType: String,
    includeExpression: String,
    converter: String? = null,
    isEnumConverter: Boolean = false
): ForcedType =
    ForcedType().apply {
        this.userType = userType
        this.includeExpression = includeExpression
        this.isEnumConverter = isEnumConverter
        this.converter = converter
    }

fun forcedTypeEnum(
    userType: String,
    includeExpression: String,
): ForcedType = forcedType(userType, includeExpression, isEnumConverter = true)

fun forcedTypes(): List<ForcedType> = listOf(
    ForcedType().apply {
        includeTypes = "TEXT"
        includeExpression = ".*_at"
        userType = "kotlinx.datetime.Instant"
        binding = "anystream.db.converter.JooqInstantBinding"
    },
    forcedType(
        userType = "anystream.models.Permission",
        includeExpression = "user_permission.value",
        converter = "anystream.db.converter.PermissionConverter"
    ),
    forcedType(
        userType = "anystream.models.Permission",
        includeExpression = "user_permission.value",
        converter = "anystream.db.converter.PermissionConverter"
    ),
    forcedType(
        userType = "anystream.models.Permission",
        includeExpression = "user_permission.value",
        converter = "anystream.db.converter.PermissionConverter"
    ),
    forcedType(
        userType = "kotlin.Set<anystream.models.Permission>",
        includeExpression = "invite_code.permissions",
        converter = "anystream.db.converter.PermissionSetConverter"
    ),
    forcedTypeEnum("anystream.models.StreamEncodingType", "stream_encoding.type"),
    forcedTypeEnum("anystream.models.MediaType", "media_type"),
    forcedTypeEnum("anystream.models.MediaLinkType", "media_link.type"),
    forcedTypeEnum("anystream.models.MediaKind", "media_kind"),
    forcedTypeEnum("anystream.models.Descriptor", "descriptor"),
    forcedType("kotlin.String", "searchable_content.id"),
    forcedType("kotlin.String", "searchable_content.content"),
)