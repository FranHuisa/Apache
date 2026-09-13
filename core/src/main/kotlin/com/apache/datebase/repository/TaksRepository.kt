package com.apache.database.repository

import com.apache.database.tables.Tasks
import java.time.LocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.springframework.stereotype.Repository

object TaskStatus {
    const val PENDING = "pending"
    const val COMPLETED = "completed"
}

data class TaskRecord(
        val id: Long,
        val userId: Long,
        val title: String,
        val description: String?,
        val status: String,
        val priority: String,
        val dueAt: LocalDateTime?,
        val completedAt: LocalDateTime?,
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime
)

@Repository
class TaskRepository {

    fun create(
            userId: Long,
            title: String,
            description: String?,
            priority: String,
            dueAt: LocalDateTime?
    ): Long = transaction {
        val now = LocalDateTime.now()

        Tasks.insert {
            it[Tasks.userId] = userId
            it[Tasks.title] = title
            it[Tasks.description] = description
            it[Tasks.status] = TaskStatus.PENDING
            it[Tasks.priority] = priority
            it[Tasks.dueAt] = dueAt
            it[Tasks.createdAt] = now
            it[Tasks.updatedAt] = now
        } get Tasks.id
    }

    fun findByUser(userId: Long, includeCompleted: Boolean = false): List<TaskRecord> =
            transaction {
                Tasks.selectAll()
                        .where {
                            if (includeCompleted) {
                                Tasks.userId eq userId
                            } else {
                                (Tasks.userId eq userId) and (Tasks.status eq TaskStatus.PENDING)
                            }
                        }
                        .orderBy(
                                Tasks.dueAt to SortOrder.ASC_NULLS_LAST,
                                Tasks.createdAt to SortOrder.DESC
                        )
                        .map { it.toRecord() }
            }

    fun findById(taskId: Long): TaskRecord? = transaction {
        Tasks.selectAll().where { Tasks.id eq taskId }.singleOrNull()?.toRecord()
    }

    fun markCompleted(taskId: Long): Boolean = transaction {
        Tasks.update({ Tasks.id eq taskId }) {
            it[Tasks.status] = TaskStatus.COMPLETED
            it[Tasks.completedAt] = LocalDateTime.now()
            it[Tasks.updatedAt] = LocalDateTime.now()
        } > 0
    }

    fun update(
            taskId: Long,
            title: String?,
            description: String?,
            priority: String?,
            dueAt: LocalDateTime?
    ): Boolean = transaction {
        val current =
                Tasks.selectAll().where { Tasks.id eq taskId }.singleOrNull()
                        ?: return@transaction false

        Tasks.update({ Tasks.id eq taskId }) {
            it[Tasks.title] = title ?: current[Tasks.title]
            it[Tasks.description] = description ?: current[Tasks.description]
            it[Tasks.priority] = priority ?: current[Tasks.priority]
            it[Tasks.dueAt] = dueAt ?: current[Tasks.dueAt]
            it[Tasks.updatedAt] = LocalDateTime.now()
        } > 0
    }

    fun delete(taskId: Long): Boolean = transaction { Tasks.deleteWhere { Tasks.id eq taskId } > 0 }
    
    private fun ResultRow.toRecord() =
            TaskRecord(
                    id = this[Tasks.id],
                    userId = this[Tasks.userId],
                    title = this[Tasks.title],
                    description = this[Tasks.description],
                    status = this[Tasks.status],
                    priority = this[Tasks.priority],
                    dueAt = this[Tasks.dueAt],
                    completedAt = this[Tasks.completedAt],
                    createdAt = this[Tasks.createdAt],
                    updatedAt = this[Tasks.updatedAt]
            )
}
