package com.example.agent.tools.gmail

import com.example.agent.model.SourceCitation
import com.example.agent.model.Tool
import com.example.agent.model.ToolPermission
import com.example.agent.model.ToolResult

private const val GMAIL_CONFIG_REQUIRED_MSG =
    "Gmail connector is not authorized. Please connect your Gmail account in Settings → Gmail Connector, then I'll access and manage your emails."

// --- 1. LIST / SEARCH EMAILS ---
class GmailListMessagesTool(private val service: GmailService) : Tool {
    override val name: String = "gmail_list_messages"
    override val description: String =
        "Search and list emails in user's Gmail inbox. Supports queries like 'is:unread', 'from:person', 'subject:meeting', 'label:INBOX', or search terms."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "query" to mapOf("type" to "STRING", "description" to "Gmail search query (e.g. 'is:unread', 'from:github', 'subject:invoice')"),
            "max_results" to mapOf("type" to "INTEGER", "description" to "Number of emails to fetch (default 10, max 25)")
        )
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GMAIL_CONFIG_REQUIRED_MSG)
        }

        val query = arguments["query"]?.toString() ?: ""
        val maxResults = (arguments["max_results"] as? Number)?.toInt() ?: 10

        return service.listMessages(query = query, maxResults = maxResults).fold(
            onSuccess = { list ->
                val sources = list.mapNotNull {
                    val id = it["id"]?.toString()
                    val subject = it["subject"]?.toString() ?: "Email"
                    if (id != null) SourceCitation(subject, "https://mail.google.com/mail/u/0/#inbox/$id") else null
                }
                ToolResult.success(
                    data = mapOf("messages" to list, "count" to list.size),
                    summary = "Retrieved ${list.size} emails${if (query.isNotBlank()) " for '$query'" else ""}",
                    sources = sources
                )
            },
            onFailure = { ToolResult.failure("GMAIL_ERROR", it.localizedMessage ?: "Failed to list emails") }
        )
    }
}

// --- 2. READ EMAIL CONTENT ---
class GmailReadMessageTool(private val service: GmailService) : Tool {
    override val name: String = "gmail_read_message"
    override val description: String =
        "Read the full content of a specific email by its message ID, including sender, recipient, subject, date, snippet, and body text."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "message_id" to mapOf("type" to "STRING", "description" to "The Gmail message ID to read")
        ),
        "required" to listOf("message_id")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GMAIL_CONFIG_REQUIRED_MSG)
        }

        val messageId = arguments["message_id"]?.toString()?.trim() ?: ""
        if (messageId.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "message_id is required.")
        }

        return service.getMessage(messageId).fold(
            onSuccess = { msg ->
                val subject = msg["subject"]?.toString() ?: "Email"
                val sources = listOf(SourceCitation(subject, "https://mail.google.com/mail/u/0/#inbox/$messageId"))
                ToolResult.success(
                    data = msg,
                    summary = "Read email '$subject' from ${msg["from"]}",
                    sources = sources
                )
            },
            onFailure = { ToolResult.failure("GMAIL_ERROR", it.localizedMessage ?: "Failed to read email") }
        )
    }
}

// --- 3. SEND EMAIL (WRITE) ---
class GmailSendMessageTool(private val service: GmailService) : Tool {
    override val name: String = "gmail_send_message"
    override val description: String =
        "Send an email from the user's Gmail account to one or more recipients with a subject and body. Requires confirmation."

    override val permission: ToolPermission = ToolPermission.WRITE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "to" to mapOf("type" to "STRING", "description" to "Recipient email address"),
            "subject" to mapOf("type" to "STRING", "description" to "Email subject line"),
            "body" to mapOf("type" to "STRING", "description" to "Text body of the email"),
            "cc" to mapOf("type" to "STRING", "description" to "CC recipient email (optional)")
        ),
        "required" to listOf("to", "subject", "body")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GMAIL_CONFIG_REQUIRED_MSG)
        }

        val to = arguments["to"]?.toString()?.trim() ?: ""
        val subject = arguments["subject"]?.toString()?.trim() ?: ""
        val body = arguments["body"]?.toString() ?: ""
        val cc = arguments["cc"]?.toString()?.trim()

        if (to.isEmpty() || subject.isEmpty() || body.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "to, subject, and body are all required.")
        }

        return service.sendMessage(to = to, subject = subject, body = body, cc = cc).fold(
            onSuccess = { res ->
                ToolResult.success(
                    data = res,
                    summary = "Sent email '$subject' to $to"
                )
            },
            onFailure = { ToolResult.failure("GMAIL_ERROR", it.localizedMessage ?: "Failed to send email") }
        )
    }
}

// --- 4. CREATE DRAFT (WRITE) ---
class GmailCreateDraftTool(private val service: GmailService) : Tool {
    override val name: String = "gmail_create_draft"
    override val description: String =
        "Create a draft email in the user's Gmail account with recipient, subject, and body."

    override val permission: ToolPermission = ToolPermission.WRITE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "to" to mapOf("type" to "STRING", "description" to "Recipient email address"),
            "subject" to mapOf("type" to "STRING", "description" to "Email subject line"),
            "body" to mapOf("type" to "STRING", "description" to "Draft text body")
        ),
        "required" to listOf("to", "subject", "body")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GMAIL_CONFIG_REQUIRED_MSG)
        }

        val to = arguments["to"]?.toString()?.trim() ?: ""
        val subject = arguments["subject"]?.toString()?.trim() ?: ""
        val body = arguments["body"]?.toString() ?: ""

        if (to.isEmpty() || subject.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "to and subject are required.")
        }

        return service.createDraft(to = to, subject = subject, body = body).fold(
            onSuccess = { res ->
                ToolResult.success(
                    data = res,
                    summary = "Created draft '$subject' for $to"
                )
            },
            onFailure = { ToolResult.failure("GMAIL_ERROR", it.localizedMessage ?: "Failed to create draft") }
        )
    }
}

// --- 5. DELETE / TRASH EMAIL (DESTRUCTIVE) ---
class GmailDeleteMessageTool(private val service: GmailService) : Tool {
    override val name: String = "gmail_delete_message"
    override val description: String =
        "Move an email to Trash or permanently delete it by message ID. DESTRUCTIVE action that requires confirmation."

    override val permission: ToolPermission = ToolPermission.DESTRUCTIVE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "message_id" to mapOf("type" to "STRING", "description" to "The Gmail message ID to delete or trash"),
            "permanent" to mapOf("type" to "BOOLEAN", "description" to "If true, permanently deletes the email; if false, moves to Trash (default false)")
        ),
        "required" to listOf("message_id")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GMAIL_CONFIG_REQUIRED_MSG)
        }

        val messageId = arguments["message_id"]?.toString()?.trim() ?: ""
        val permanent = (arguments["permanent"] as? Boolean) ?: false

        if (messageId.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "message_id is required.")
        }

        return service.deleteMessage(messageId = messageId, permanent = permanent).fold(
            onSuccess = { res ->
                ToolResult.success(
                    data = res,
                    summary = if (permanent) "Permanently deleted email $messageId" else "Moved email $messageId to Trash"
                )
            },
            onFailure = { ToolResult.failure("GMAIL_ERROR", it.localizedMessage ?: "Failed to delete email") }
        )
    }
}

// --- 6. MODIFY LABELS (MARK AS READ, STAR, ARCHIVE) ---
class GmailModifyLabelsTool(private val service: GmailService) : Tool {
    override val name: String = "gmail_modify_labels"
    override val description: String =
        "Modify email labels: mark as read (removes 'UNREAD'), star an email (adds 'STARRED'), or archive (removes 'INBOX')."

    override val permission: ToolPermission = ToolPermission.WRITE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "message_id" to mapOf("type" to "STRING", "description" to "The Gmail message ID"),
            "add_labels" to mapOf("type" to "ARRAY", "items" to mapOf("type" to "STRING"), "description" to "Labels to add (e.g. 'STARRED', 'IMPORTANT')"),
            "remove_labels" to mapOf("type" to "ARRAY", "items" to mapOf("type" to "STRING"), "description" to "Labels to remove (e.g. 'UNREAD', 'INBOX')")
        ),
        "required" to listOf("message_id")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GMAIL_CONFIG_REQUIRED_MSG)
        }

        val messageId = arguments["message_id"]?.toString()?.trim() ?: ""
        @Suppress("UNCHECKED_CAST")
        val addLabels = (arguments["add_labels"] as? List<String>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val removeLabels = (arguments["remove_labels"] as? List<String>) ?: emptyList()

        if (messageId.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "message_id is required.")
        }

        return service.modifyLabels(messageId, addLabels, removeLabels).fold(
            onSuccess = { res ->
                val actionDesc = when {
                    removeLabels.contains("UNREAD") -> "Marked email as read"
                    addLabels.contains("STARRED") -> "Starred email"
                    removeLabels.contains("INBOX") -> "Archived email"
                    else -> "Updated labels for email $messageId"
                }
                ToolResult.success(data = res, summary = actionDesc)
            },
            onFailure = { ToolResult.failure("GMAIL_ERROR", it.localizedMessage ?: "Failed to modify email labels") }
        )
    }
}
