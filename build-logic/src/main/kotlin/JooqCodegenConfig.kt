import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.Logging
import org.jooq.meta.jaxb.Property


fun Configuration.anystreamConfig(dbUrl: String) {
    logging = Logging.ERROR
    jdbc.apply {
        driver = "org.sqlite.JDBC"
        url = "${dbUrl}?foreign_keys=on;"
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
            forcedTypes.addAll(forcedTypes())
            excludes = listOf(
                // Exclude flyway migration tables
                "flyway_.*",
                // Exclude search meta tables
                "searchable_content_.*",
            ).joinToString("|")
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
    ForcedType().apply {
        userType = "kotlin.time.Duration"
        binding = "anystream.db.converter.DurationBinding"
        includeTypes = "TEXT"
        includeExpression = listOf(
            "metadata.runtime",
            "playback_state.runtime",
            "playback_state.position",
            "stream_encoding.duration",
        ).joinToString("|")
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
    forcedTypeEnum("anystream.models.AuthType", "user.auth_type"),
    forcedTypeEnum("anystream.models.TagType", "tag.type"),
    forcedTypeEnum("anystream.models.CreditType", "metadata_credit.type"),
    forcedTypeEnum("anystream.models.CreditJob", "metadata_credit.job"),
    forcedType("kotlin.String", "searchable_content.id"),
    forcedType("kotlin.String", "searchable_content.content"),
)