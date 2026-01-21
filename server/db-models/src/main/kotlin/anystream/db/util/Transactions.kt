/**
 * AnyStream
 * Copyright (C) 2025 AnyStream Maintainers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package anystream.db.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("anystream.db.util.Transactions")

/**
 * Execute a suspend block within a database transaction.
 *
 * This function provides a coroutine-friendly way to execute multiple database
 * operations atomically. If any exception is thrown within the block, the
 * entire transaction will be rolled back.
 *
 * @param block The suspend function to execute within the transaction.
 *              Receives a DSLContext scoped to the transaction.
 * @return The result of the block.
 * @throws Exception Any exception thrown by the block will propagate after rollback.
 */
suspend fun <T> DSLContext.transactionAsync(
    block: suspend (DSLContext) -> T
): T = withContext(Dispatchers.IO) {
    transactionResult { config ->
        val txContext = DSL.using(config)
        kotlinx.coroutines.runBlocking {
            block(txContext)
        }
    }
}

/**
 * Execute a suspend block within a database transaction, returning a Result type.
 *
 * This variant catches exceptions and returns them as failures instead of throwing.
 * Useful for operations where you want to handle errors without try-catch blocks.
 *
 * @param block The suspend function to execute within the transaction.
 * @return Result.success with the block's result, or Result.failure with the exception.
 */
suspend fun <T> DSLContext.transactionAsyncCatching(
    block: suspend (DSLContext) -> T
): Result<T> = runCatching {
    transactionAsync(block)
}

/**
 * Result of a transactional delete operation.
 *
 * @property deleted The number of records deleted.
 * @property success Whether the operation completed successfully.
 * @property details Optional details about what was deleted (for logging).
 */
data class DeleteResult(
    val deleted: Int,
    val success: Boolean = true,
    val details: Map<String, Int> = emptyMap()
) {
    companion object {
        fun success(deleted: Int, details: Map<String, Int> = emptyMap()) =
            DeleteResult(deleted = deleted, success = true, details = details)

        fun failure() =
            DeleteResult(deleted = 0, success = false)
    }
}

/**
 * Execute a delete operation within a transaction, with logging.
 *
 * @param entityType A description of what is being deleted (for logging).
 * @param entityId The ID of the entity being deleted.
 * @param block The delete operations to execute.
 * @return DeleteResult with the count and success status.
 */
suspend fun DSLContext.transactionDelete(
    entityType: String,
    entityId: String,
    block: suspend (DSLContext) -> Map<String, Int>
): DeleteResult {
    return try {
        val details = transactionAsync(block)
        val totalDeleted = details.values.sum()
        logger.info("Deleted {} {} ({})", entityType, entityId, details)
        DeleteResult.success(totalDeleted, details)
    } catch (e: Exception) {
        logger.error("Failed to delete {} {}: {}", entityType, entityId, e.message, e)
        DeleteResult.failure()
    }
}
