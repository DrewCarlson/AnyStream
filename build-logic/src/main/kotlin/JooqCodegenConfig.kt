import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.Logging

fun Configuration.anystreamConfig(dbUrl: String) {
    logging = Logging.ERROR
    jdbc.apply {
        driver = "org.sqlite.JDBC"
        url = "$dbUrl?foreign_keys=on;"
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
    isEnumConverter: Boolean = false,
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

fun forcedTypes(): List<ForcedType> =
    listOf(
        ForcedType().apply {
            includeTypes = "TEXT"
            includeExpression = ".*_at"
            userType = "kotlin.time.Instant"
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
            converter = "anystream.db.converter.PermissionConverter",
        ),
        forcedType(
            userType = "kotlin.Set<anystream.models.Permission>",
            includeExpression = "invite_code.permissions",
            converter = "anystream.db.converter.PermissionSetConverter",
        ),
        forcedTypeEnum("anystream.models.StreamEncodingType", "stream_encoding.type"),
        forcedTypeEnum("anystream.models.MediaType", "media_type"),
        forcedTypeEnum("anystream.models.MediaLinkType", "media_link.type"),
        forcedTypeEnum("anystream.models.MediaKind", "media_kind"),
        forcedTypeEnum("anystream.models.Descriptor", "descriptor"),
        forcedTypeEnum("anystream.models.AuthSource", "user.auth_source"),
        forcedTypeEnum("anystream.models.TagType", "tag.type"),
        forcedTypeEnum("anystream.models.CreditType", "metadata_credit.type"),
        forcedTypeEnum("anystream.models.CreditJob", "metadata_credit.job"),
        forcedType("kotlin.String", "searchable_content.content"),
        // ID type converters
        forcedType(
            userType = "anystream.models.UserId",
            includeExpression = listOf(
                "user.id",
                "user_permission.user_id",
                "session.user_id",
                "invite_code.created_by_user_id",
                "playback_state.user_id",
            ).joinToString("|"),
            converter = "anystream.db.converter.UserIdConverter",
        ),
        forcedType(
            userType = "anystream.models.SessionId",
            includeExpression = "session.id",
            converter = "anystream.db.converter.SessionIdConverter",
        ),
        forcedType(
            userType = "anystream.models.MetadataId",
            includeExpression = listOf(
                "metadata.id",
                "metadata.root_id",
                "metadata.parent_id",
                "media_link.metadata_id",
                "media_link.root_metadata_id",
                "metadata_company.metadata_id",
                "metadata_genre.metadata_id",
                "metadata_credit.metadata_id",
                "playback_state.metadata_id",
            ).joinToString("|"),
            converter = "anystream.db.converter.MetadataIdConverter",
        ),
        forcedType("kotlin.String", "searchable_content.id"),
        forcedType(
            userType = "anystream.models.MediaLinkId",
            includeExpression = listOf(
                "media_link.id",
                "playback_state.media_link_id",
                "stream_encoding.media_link_id",
            ).joinToString("|"),
            converter = "anystream.db.converter.MediaLinkIdConverter",
        ),
        forcedType(
            userType = "anystream.models.LibraryId",
            includeExpression = listOf(
                "library.id",
                "directory.library_id",
            ).joinToString("|"),
            converter = "anystream.db.converter.LibraryIdConverter",
        ),
        forcedType(
            userType = "anystream.models.DirectoryId",
            includeExpression = listOf(
                "directory.id",
                "directory.parent_id",
                "media_link.directory_id",
            ).joinToString("|"),
            converter = "anystream.db.converter.DirectoryIdConverter",
        ),
        forcedType(
            userType = "anystream.models.TagId",
            includeExpression = listOf(
                "tag.id",
                "metadata_company.company_id",
                "metadata_genre.genre_id",
                "metadata_credit.person_id",
            ).joinToString("|"),
            converter = "anystream.db.converter.TagIdConverter",
        ),
        forcedType(
            userType = "anystream.models.PlaybackStateId",
            includeExpression = "playback_state.id",
            converter = "anystream.db.converter.PlaybackStateIdConverter",
        ),
        forcedType(
            userType = "anystream.models.StreamEncodingId",
            includeExpression = "stream_encoding.id",
            converter = "anystream.db.converter.StreamEncodingIdConverter",
        ),
    )
