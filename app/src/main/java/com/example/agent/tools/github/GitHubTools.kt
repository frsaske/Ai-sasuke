package com.example.agent.tools.github

import android.content.Context
import com.example.agent.model.SourceCitation
import com.example.agent.model.Tool
import com.example.agent.model.ToolPermission
import com.example.agent.model.ToolResult
import com.example.util.FileTransferManager

private const val GITHUB_KEY_NOT_SET_MSG = "GitHub key is not set. Please set your GitHub API key in Settings, then I'll do it."

// --- 1. LIST REPOS ---
class GitHubListReposTool(private val service: GitHubService) : Tool {
    override val name: String = "github_list_repos"
    override val description: String =
        "List repositories accessible to the user or organization on GitHub. Returns names, descriptions, stars, and URLs."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "sort" to mapOf("type" to "STRING", "description" to "Sort by: 'updated', 'created', 'pushed', 'full_name'"),
            "per_page" to mapOf("type" to "INTEGER", "description" to "Number of repos to return (default 10)")
        )
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GITHUB_KEY_NOT_SET_MSG)
        }

        val sort = arguments["sort"]?.toString() ?: "updated"
        val perPage = (arguments["per_page"] as? Number)?.toInt() ?: 10

        return service.listRepositories(sort = sort, perPage = perPage).fold(
            onSuccess = { list ->
                val sources = list.mapNotNull {
                    val url = it["html_url"]?.toString()
                    val name = it["full_name"]?.toString()
                    if (url != null && name != null) SourceCitation(name, url) else null
                }
                ToolResult.success(
                    data = mapOf("repositories" to list, "count" to list.size),
                    summary = "Retrieved ${list.size} repositories",
                    sources = sources
                )
            },
            onFailure = { ToolResult.failure("GITHUB_ERROR", it.localizedMessage ?: "Failed to list repos") }
        )
    }
}

// --- 2. REPO INFO ---
class GitHubRepoInfoTool(private val service: GitHubService) : Tool {
    override val name: String = "github_repo_info"
    override val description: String =
        "Get detailed information about a specific repository, including description, stars, forks, default branch, and stats."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "owner" to mapOf("type" to "STRING", "description" to "GitHub repository owner (username or org)"),
            "repo" to mapOf("type" to "STRING", "description" to "Repository name")
        ),
        "required" to listOf("owner", "repo")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GITHUB_KEY_NOT_SET_MSG)
        }

        val owner = arguments["owner"]?.toString()?.trim() ?: ""
        val repo = arguments["repo"]?.toString()?.trim() ?: ""
        if (owner.isEmpty() || repo.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "Both owner and repo are required.")
        }

        return service.getRepositoryInfo(owner, repo).fold(
            onSuccess = { info ->
                val sources = info["html_url"]?.toString()?.let { listOf(SourceCitation("$owner/$repo", it)) } ?: emptyList()
                ToolResult.success(
                    data = info,
                    summary = "$owner/$repo (${info["stars"]} ★)",
                    sources = sources
                )
            },
            onFailure = { ToolResult.failure("GITHUB_ERROR", it.localizedMessage ?: "Failed to get repo info") }
        )
    }
}

// --- 3. LIST FILES ---
class GitHubListFilesTool(private val service: GitHubService) : Tool {
    override val name: String = "github_list_files"
    override val description: String =
        "List files and directories inside a GitHub repository path."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "owner" to mapOf("type" to "STRING", "description" to "Repository owner"),
            "repo" to mapOf("type" to "STRING", "description" to "Repository name"),
            "path" to mapOf("type" to "STRING", "description" to "Directory path inside repository (empty string for root)"),
            "ref" to mapOf("type" to "STRING", "description" to "Branch or tag name (optional)")
        ),
        "required" to listOf("owner", "repo")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GITHUB_KEY_NOT_SET_MSG)
        }

        val owner = arguments["owner"]?.toString()?.trim() ?: ""
        val repo = arguments["repo"]?.toString()?.trim() ?: ""
        val path = arguments["path"]?.toString()?.trim() ?: ""
        val ref = arguments["ref"]?.toString()?.trim()

        if (owner.isEmpty() || repo.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "Both owner and repo are required.")
        }

        return service.listFiles(owner, repo, path, ref).fold(
            onSuccess = { list ->
                ToolResult.success(
                    data = mapOf("files" to list, "count" to list.size),
                    summary = "Found ${list.size} items in ${if (path.isEmpty()) "root" else path}"
                )
            },
            onFailure = { ToolResult.failure("GITHUB_ERROR", it.localizedMessage ?: "Failed to list files") }
        )
    }
}

// --- 4. READ FILE ---
class GitHubReadFileTool(private val service: GitHubService) : Tool {
    override val name: String = "github_read_file"
    override val description: String =
        "Read and decode the content of a file in a GitHub repository (such as README.md, build files, source code)."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "owner" to mapOf("type" to "STRING", "description" to "Repository owner"),
            "repo" to mapOf("type" to "STRING", "description" to "Repository name"),
            "path" to mapOf("type" to "STRING", "description" to "Path to the file (e.g. 'README.md')"),
            "ref" to mapOf("type" to "STRING", "description" to "Branch, commit SHA, or tag (optional)")
        ),
        "required" to listOf("owner", "repo", "path")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GITHUB_KEY_NOT_SET_MSG)
        }

        val owner = arguments["owner"]?.toString()?.trim() ?: ""
        val repo = arguments["repo"]?.toString()?.trim() ?: ""
        val path = arguments["path"]?.toString()?.trim() ?: ""
        val ref = arguments["ref"]?.toString()?.trim()

        if (owner.isEmpty() || repo.isEmpty() || path.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "owner, repo, and path are all required.")
        }

        return service.readFile(owner, repo, path, ref).fold(
            onSuccess = { fileData ->
                ToolResult.success(
                    data = fileData,
                    summary = "Read $path (${fileData["size"]} bytes)"
                )
            },
            onFailure = { ToolResult.failure("GITHUB_ERROR", it.localizedMessage ?: "Failed to read file") }
        )
    }
}

// --- 5. LIST COMMITS ---
class GitHubListCommitsTool(private val service: GitHubService) : Tool {
    override val name: String = "github_list_commits"
    override val description: String =
        "List recent commit messages, authors, and dates from a repository."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "owner" to mapOf("type" to "STRING", "description" to "Repository owner"),
            "repo" to mapOf("type" to "STRING", "description" to "Repository name"),
            "per_page" to mapOf("type" to "INTEGER", "description" to "Number of commits (default 5)")
        ),
        "required" to listOf("owner", "repo")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GITHUB_KEY_NOT_SET_MSG)
        }

        val owner = arguments["owner"]?.toString()?.trim() ?: ""
        val repo = arguments["repo"]?.toString()?.trim() ?: ""
        val perPage = (arguments["per_page"] as? Number)?.toInt() ?: 5

        if (owner.isEmpty() || repo.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "owner and repo are required.")
        }

        return service.listCommits(owner, repo, perPage).fold(
            onSuccess = { list ->
                ToolResult.success(
                    data = mapOf("commits" to list, "count" to list.size),
                    summary = "Retrieved ${list.size} commits from $owner/$repo"
                )
            },
            onFailure = { ToolResult.failure("GITHUB_ERROR", it.localizedMessage ?: "Failed to list commits") }
        )
    }
}

// --- 6. LIST ISSUES ---
class GitHubListIssuesTool(private val service: GitHubService) : Tool {
    override val name: String = "github_list_issues"
    override val description: String =
        "List open or closed issues in a GitHub repository."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "owner" to mapOf("type" to "STRING", "description" to "Repository owner"),
            "repo" to mapOf("type" to "STRING", "description" to "Repository name"),
            "state" to mapOf("type" to "STRING", "description" to "'open', 'closed', or 'all' (default 'open')"),
            "per_page" to mapOf("type" to "INTEGER", "description" to "Max count (default 10)")
        ),
        "required" to listOf("owner", "repo")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GITHUB_KEY_NOT_SET_MSG)
        }

        val owner = arguments["owner"]?.toString()?.trim() ?: ""
        val repo = arguments["repo"]?.toString()?.trim() ?: ""
        val state = arguments["state"]?.toString() ?: "open"
        val perPage = (arguments["per_page"] as? Number)?.toInt() ?: 10

        if (owner.isEmpty() || repo.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "owner and repo are required.")
        }

        return service.listIssues(owner, repo, state, perPage).fold(
            onSuccess = { list ->
                ToolResult.success(
                    data = mapOf("issues" to list, "count" to list.size),
                    summary = "Retrieved ${list.size} $state issues"
                )
            },
            onFailure = { ToolResult.failure("GITHUB_ERROR", it.localizedMessage ?: "Failed to list issues") }
        )
    }
}

// --- 7. LIST BRANCHES ---
class GitHubListBranchesTool(private val service: GitHubService) : Tool {
    override val name: String = "github_list_branches"
    override val description: String = "List all branches in a GitHub repository."
    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "owner" to mapOf("type" to "STRING", "description" to "Repository owner"),
            "repo" to mapOf("type" to "STRING", "description" to "Repository name")
        ),
        "required" to listOf("owner", "repo")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GITHUB_KEY_NOT_SET_MSG)
        }

        val owner = arguments["owner"]?.toString()?.trim() ?: ""
        val repo = arguments["repo"]?.toString()?.trim() ?: ""

        if (owner.isEmpty() || repo.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "owner and repo are required.")
        }

        return service.listBranches(owner, repo).fold(
            onSuccess = { list ->
                ToolResult.success(
                    data = mapOf("branches" to list),
                    summary = "Found ${list.size} branches"
                )
            },
            onFailure = { ToolResult.failure("GITHUB_ERROR", it.localizedMessage ?: "Failed to list branches") }
        )
    }
}

// --- 8. LIST RELEASES ---
class GitHubListReleasesTool(private val service: GitHubService) : Tool {
    override val name: String = "github_list_releases"
    override val description: String = "List published releases in a GitHub repository."
    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "owner" to mapOf("type" to "STRING", "description" to "Repository owner"),
            "repo" to mapOf("type" to "STRING", "description" to "Repository name")
        ),
        "required" to listOf("owner", "repo")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GITHUB_KEY_NOT_SET_MSG)
        }

        val owner = arguments["owner"]?.toString()?.trim() ?: ""
        val repo = arguments["repo"]?.toString()?.trim() ?: ""

        if (owner.isEmpty() || repo.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "owner and repo are required.")
        }

        return service.listReleases(owner, repo).fold(
            onSuccess = { list ->
                ToolResult.success(
                    data = mapOf("releases" to list),
                    summary = "Found ${list.size} releases"
                )
            },
            onFailure = { ToolResult.failure("GITHUB_ERROR", it.localizedMessage ?: "Failed to list releases") }
        )
    }
}

// --- 9. CREATE ISSUE (WRITE) ---
class GitHubCreateIssueTool(private val service: GitHubService) : Tool {
    override val name: String = "github_create_issue"
    override val description: String = "Create a new issue in a GitHub repository. Requires user confirmation."
    override val permission: ToolPermission = ToolPermission.WRITE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "owner" to mapOf("type" to "STRING", "description" to "Repository owner"),
            "repo" to mapOf("type" to "STRING", "description" to "Repository name"),
            "title" to mapOf("type" to "STRING", "description" to "Issue title"),
            "body" to mapOf("type" to "STRING", "description" to "Issue description / body text")
        ),
        "required" to listOf("owner", "repo", "title", "body")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GITHUB_KEY_NOT_SET_MSG)
        }

        val owner = arguments["owner"]?.toString()?.trim() ?: ""
        val repo = arguments["repo"]?.toString()?.trim() ?: ""
        val title = arguments["title"]?.toString()?.trim() ?: ""
        val body = arguments["body"]?.toString()?.trim() ?: ""

        if (owner.isEmpty() || repo.isEmpty() || title.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "owner, repo, and title are required.")
        }

        return service.createIssue(owner, repo, title, body).fold(
            onSuccess = { res ->
                val url = res["html_url"]?.toString()
                val sources = if (url != null) listOf(SourceCitation("Issue #${res["number"]}", url)) else emptyList()
                ToolResult.success(
                    data = res,
                    summary = "Created issue #${res["number"]} in $owner/$repo",
                    sources = sources
                )
            },
            onFailure = { ToolResult.failure("GITHUB_ERROR", it.localizedMessage ?: "Failed to create issue") }
        )
    }
}

// --- 10. CREATE FILE (WRITE) ---
class GitHubCreateFileTool(private val service: GitHubService) : Tool {
    override val name: String = "github_create_file"
    override val description: String = "Create a new file in a GitHub repository. Requires user confirmation."
    override val permission: ToolPermission = ToolPermission.WRITE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "owner" to mapOf("type" to "STRING", "description" to "Repository owner"),
            "repo" to mapOf("type" to "STRING", "description" to "Repository name"),
            "path" to mapOf("type" to "STRING", "description" to "File path"),
            "content" to mapOf("type" to "STRING", "description" to "Text content for the file"),
            "message" to mapOf("type" to "STRING", "description" to "Commit message"),
            "branch" to mapOf("type" to "STRING", "description" to "Target branch (optional)")
        ),
        "required" to listOf("owner", "repo", "path", "content")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GITHUB_KEY_NOT_SET_MSG)
        }

        val owner = arguments["owner"]?.toString()?.trim() ?: ""
        val repo = arguments["repo"]?.toString()?.trim() ?: ""
        val path = arguments["path"]?.toString()?.trim() ?: ""
        val content = arguments["content"]?.toString() ?: ""
        val message = arguments["message"]?.toString() ?: "Create $path via SasukeX"
        val branch = arguments["branch"]?.toString()

        if (owner.isEmpty() || repo.isEmpty() || path.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "owner, repo, and path are required.")
        }

        return service.createOrUpdateFile(owner, repo, path, content, message, sha = null, branch = branch).fold(
            onSuccess = { res ->
                ToolResult.success(data = res, summary = "Created file $path in $owner/$repo")
            },
            onFailure = { ToolResult.failure("GITHUB_ERROR", it.localizedMessage ?: "Failed to create file") }
        )
    }
}

// --- 11. UPDATE FILE (WRITE) ---
class GitHubUpdateFileTool(private val service: GitHubService) : Tool {
    override val name: String = "github_update_file"
    override val description: String = "Update an existing file in a GitHub repository using its SHA. Requires user confirmation."
    override val permission: ToolPermission = ToolPermission.WRITE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "owner" to mapOf("type" to "STRING", "description" to "Repository owner"),
            "repo" to mapOf("type" to "STRING", "description" to "Repository name"),
            "path" to mapOf("type" to "STRING", "description" to "File path (e.g. 'README.md')"),
            "content" to mapOf("type" to "STRING", "description" to "Updated text content"),
            "message" to mapOf("type" to "STRING", "description" to "Commit message"),
            "sha" to mapOf("type" to "STRING", "description" to "The blob SHA of the file being replaced"),
            "branch" to mapOf("type" to "STRING", "description" to "Target branch (optional)")
        ),
        "required" to listOf("owner", "repo", "path", "content", "sha")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GITHUB_KEY_NOT_SET_MSG)
        }

        val owner = arguments["owner"]?.toString()?.trim() ?: ""
        val repo = arguments["repo"]?.toString()?.trim() ?: ""
        val path = arguments["path"]?.toString()?.trim() ?: ""
        val content = arguments["content"]?.toString() ?: ""
        val message = arguments["message"]?.toString() ?: "Update $path via SasukeX"
        val sha = arguments["sha"]?.toString()?.trim() ?: ""
        val branch = arguments["branch"]?.toString()

        if (owner.isEmpty() || repo.isEmpty() || path.isEmpty() || sha.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "owner, repo, path, and sha are required to update a file.")
        }

        return service.createOrUpdateFile(owner, repo, path, content, message, sha = sha, branch = branch).fold(
            onSuccess = { res ->
                ToolResult.success(data = res, summary = "Updated file $path in $owner/$repo")
            },
            onFailure = { ToolResult.failure("GITHUB_ERROR", it.localizedMessage ?: "Failed to update file") }
        )
    }
}

// --- 12. DELETE FILE (DESTRUCTIVE) ---
class GitHubDeleteFileTool(private val service: GitHubService) : Tool {
    override val name: String = "github_delete_file"
    override val description: String = "Delete a file from a GitHub repository. DESTRUCTIVE action that strictly requires confirmation."
    override val permission: ToolPermission = ToolPermission.DESTRUCTIVE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "owner" to mapOf("type" to "STRING", "description" to "Repository owner"),
            "repo" to mapOf("type" to "STRING", "description" to "Repository name"),
            "path" to mapOf("type" to "STRING", "description" to "File path to delete"),
            "message" to mapOf("type" to "STRING", "description" to "Commit message"),
            "sha" to mapOf("type" to "STRING", "description" to "The blob SHA of the file being deleted"),
            "branch" to mapOf("type" to "STRING", "description" to "Target branch (optional)")
        ),
        "required" to listOf("owner", "repo", "path", "sha")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GITHUB_KEY_NOT_SET_MSG)
        }

        val owner = arguments["owner"]?.toString()?.trim() ?: ""
        val repo = arguments["repo"]?.toString()?.trim() ?: ""
        val path = arguments["path"]?.toString()?.trim() ?: ""
        val sha = arguments["sha"]?.toString()?.trim() ?: ""
        val message = arguments["message"]?.toString() ?: "Delete $path via SasukeX"
        val branch = arguments["branch"]?.toString()

        if (owner.isEmpty() || repo.isEmpty() || path.isEmpty() || sha.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "owner, repo, path, and sha are required.")
        }

        return service.deleteFile(owner, repo, path, message, sha, branch).fold(
            onSuccess = { res ->
                ToolResult.success(data = res, summary = "Deleted file $path in $owner/$repo")
            },
            onFailure = { ToolResult.failure("GITHUB_ERROR", it.localizedMessage ?: "Failed to delete file") }
        )
    }
}

// --- 13. CREATE BRANCH (WRITE) ---
class GitHubCreateBranchTool(private val service: GitHubService) : Tool {
    override val name: String = "github_create_branch"
    override val description: String = "Create a new branch in a repository. Requires user confirmation."
    override val permission: ToolPermission = ToolPermission.WRITE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "owner" to mapOf("type" to "STRING", "description" to "Repository owner"),
            "repo" to mapOf("type" to "STRING", "description" to "Repository name"),
            "branch" to mapOf("type" to "STRING", "description" to "New branch name"),
            "from_sha" to mapOf("type" to "STRING", "description" to "Base commit SHA from which to branch")
        ),
        "required" to listOf("owner", "repo", "branch", "from_sha")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GITHUB_KEY_NOT_SET_MSG)
        }

        val owner = arguments["owner"]?.toString()?.trim() ?: ""
        val repo = arguments["repo"]?.toString()?.trim() ?: ""
        val branch = arguments["branch"]?.toString()?.trim() ?: ""
        val fromSha = arguments["from_sha"]?.toString()?.trim() ?: ""

        if (owner.isEmpty() || repo.isEmpty() || branch.isEmpty() || fromSha.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "owner, repo, branch, and from_sha are required.")
        }

        return service.createBranch(owner, repo, branch, fromSha).fold(
            onSuccess = { res ->
                ToolResult.success(data = res, summary = "Created branch $branch in $owner/$repo")
            },
            onFailure = { ToolResult.failure("GITHUB_ERROR", it.localizedMessage ?: "Failed to create branch") }
        )
    }
}

// --- 14. CREATE PULL REQUEST (WRITE) ---
class GitHubCreatePullRequestTool(private val service: GitHubService) : Tool {
    override val name: String = "github_create_pull_request"
    override val description: String = "Create a new pull request on GitHub. Requires user confirmation."
    override val permission: ToolPermission = ToolPermission.WRITE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "owner" to mapOf("type" to "STRING", "description" to "Repository owner"),
            "repo" to mapOf("type" to "STRING", "description" to "Repository name"),
            "title" to mapOf("type" to "STRING", "description" to "PR title"),
            "body" to mapOf("type" to "STRING", "description" to "PR description / body"),
            "head" to mapOf("type" to "STRING", "description" to "Source branch name containing changes"),
            "base" to mapOf("type" to "STRING", "description" to "Target branch to merge into (e.g. 'main')")
        ),
        "required" to listOf("owner", "repo", "title", "body", "head", "base")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GITHUB_KEY_NOT_SET_MSG)
        }

        val owner = arguments["owner"]?.toString()?.trim() ?: ""
        val repo = arguments["repo"]?.toString()?.trim() ?: ""
        val title = arguments["title"]?.toString()?.trim() ?: ""
        val body = arguments["body"]?.toString()?.trim() ?: ""
        val head = arguments["head"]?.toString()?.trim() ?: ""
        val base = arguments["base"]?.toString()?.trim() ?: ""

        if (owner.isEmpty() || repo.isEmpty() || title.isEmpty() || head.isEmpty() || base.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "owner, repo, title, head, and base are required.")
        }

        return service.createPullRequest(owner, repo, title, body, head, base).fold(
            onSuccess = { res ->
                val url = res["html_url"]?.toString()
                val sources = if (url != null) listOf(SourceCitation("PR #${res["number"]}", url)) else emptyList()
                ToolResult.success(
                    data = res,
                    summary = "Created PR #${res["number"]} in $owner/$repo",
                    sources = sources
                )
            },
            onFailure = { ToolResult.failure("GITHUB_ERROR", it.localizedMessage ?: "Failed to create PR") }
        )
    }
}

// --- 15. DOWNLOAD FILE TO DEVICE ---
class GitHubDownloadFileTool(
    private val service: GitHubService,
    private val context: Context
) : Tool {
    override val name: String = "github_download_file"
    override val description: String =
        "Download a file directly from a GitHub repository to the user's Android device (Downloads folder). Produces a Download File Card and sends a download notification."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "owner" to mapOf("type" to "STRING", "description" to "Repository owner"),
            "repo" to mapOf("type" to "STRING", "description" to "Repository name"),
            "path" to mapOf("type" to "STRING", "description" to "Path to the file to download (e.g. 'main.py' or 'session/creds.json')"),
            "ref" to mapOf("type" to "STRING", "description" to "Branch, commit SHA, or tag (optional)")
        ),
        "required" to listOf("owner", "repo", "path")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GITHUB_KEY_NOT_SET_MSG)
        }

        val owner = arguments["owner"]?.toString()?.trim() ?: ""
        val repo = arguments["repo"]?.toString()?.trim() ?: ""
        val path = arguments["path"]?.toString()?.trim() ?: ""
        val ref = arguments["ref"]?.toString()?.trim()

        if (owner.isEmpty() || repo.isEmpty() || path.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "owner, repo, and path are all required.")
        }

        val fileName = path.substringAfterLast('/')
        return service.downloadRawFile(owner, repo, path, ref).fold(
            onSuccess = { bytes ->
                val savedFile = FileTransferManager.saveDownloadedFile(context, fileName, bytes)
                FileTransferManager.postDownloadNotification(context, savedFile)

                val preview = if (bytes.size < 4000) {
                    try { String(bytes, Charsets.UTF_8) } catch (_: Exception) { null }
                } else null

                val data = mapOf(
                    "downloaded_file" to mapOf(
                        "file_name" to savedFile.name,
                        "file_path" to savedFile.absolutePath,
                        "file_size" to savedFile.length(),
                        "mime_type" to FileTransferManager.getMimeType(savedFile.name),
                        "status" to "SUCCESS",
                        "extension" to savedFile.extension
                    ),
                    "file_name" to savedFile.name,
                    "file_size" to savedFile.length(),
                    "file_path" to savedFile.absolutePath,
                    "code_preview" to preview
                )

                ToolResult.success(
                    data = data,
                    summary = "Downloaded ${savedFile.name} (${FileTransferManager.formatFileSize(savedFile.length())}) to device Downloads"
                )
            },
            onFailure = { ToolResult.failure("DOWNLOAD_ERROR", it.localizedMessage ?: "Failed to download file from GitHub") }
        )
    }
}

// --- 16. UPLOAD LOCAL ATTACHED FILE TO GITHUB ---
class GitHubUploadLocalFileTool(
    private val service: GitHubService,
    private val context: Context
) : Tool {
    override val name: String = "github_upload_local_file"
    override val description: String =
        "Upload a locally attached file (from the user's device) directly to a repository without consuming tokens. Use when the user attached a file (such as creds.json, main.py, etc.) and requested to add or upload it to a repository folder."

    override val permission: ToolPermission = ToolPermission.WRITE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "owner" to mapOf("type" to "STRING", "description" to "Repository owner"),
            "repo" to mapOf("type" to "STRING", "description" to "Repository name"),
            "path" to mapOf("type" to "STRING", "description" to "Destination path in repository (e.g. 'session/session1/creds.json')"),
            "local_file_name" to mapOf("type" to "STRING", "description" to "The name of the locally attached file (e.g. 'creds.json')"),
            "message" to mapOf("type" to "STRING", "description" to "Commit message (optional)"),
            "branch" to mapOf("type" to "STRING", "description" to "Target branch (optional)")
        ),
        "required" to listOf("owner", "repo", "path", "local_file_name")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GITHUB_KEY_NOT_SET_MSG)
        }

        val owner = arguments["owner"]?.toString()?.trim() ?: ""
        val repo = arguments["repo"]?.toString()?.trim() ?: ""
        val path = arguments["path"]?.toString()?.trim() ?: ""
        val localFileName = arguments["local_file_name"]?.toString()?.trim() ?: ""
        val message = arguments["message"]?.toString()?.ifBlank { "Upload $localFileName to $path via SasukeX" } ?: "Upload $localFileName to $path via SasukeX"
        val branch = arguments["branch"]?.toString()

        if (owner.isEmpty() || repo.isEmpty() || path.isEmpty() || localFileName.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "owner, repo, path, and local_file_name are all required.")
        }

        val bytes = FileTransferManager.getAttachedFileBytes(context, localFileName)
            ?: return ToolResult.failure("FILE_NOT_FOUND", "Attached file '$localFileName' was not found on device. Please attach it again.")

        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

        return service.createOrUpdateBinaryFile(owner, repo, path, base64, message, sha = null, branch = branch).fold(
            onSuccess = { res ->
                ToolResult.success(
                    data = res + mapOf(
                        "uploaded_file" to localFileName,
                        "target_path" to path,
                        "size_bytes" to bytes.size
                    ),
                    summary = "Successfully uploaded $localFileName (${FileTransferManager.formatFileSize(bytes.size.toLong())}) to $owner/$repo at $path"
                )
            },
            onFailure = { ToolResult.failure("UPLOAD_ERROR", it.localizedMessage ?: "Failed to upload file to GitHub") }
        )
    }
}

// --- 17. READ ATTACHED FILE CONTENT ---
class ReadAttachedFileTool(
    private val context: Context
) : Tool {
    override val name: String = "read_attached_file"
    override val description: String =
        "Read the text content of a locally attached file ONLY if the user explicitly asks to read, inspect, or explain the file content. Do NOT call this tool for file upload or transfer operations."

    override val permission: ToolPermission = ToolPermission.READ_ONLY

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "file_name" to mapOf("type" to "STRING", "description" to "The name of the locally attached file to read")
        ),
        "required" to listOf("file_name")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val fileName = arguments["file_name"]?.toString()?.trim() ?: ""
        if (fileName.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "file_name is required.")
        }

        val text = FileTransferManager.getAttachedFileText(context, fileName)
            ?: return ToolResult.failure("FILE_NOT_FOUND", "Could not read attached file '$fileName' as text.")

        val maxChars = 12000
        val truncated = if (text.length > maxChars) {
            text.take(maxChars) + "\n\n... [Content truncated, total length: ${text.length} chars]"
        } else text

        return ToolResult.success(
            data = mapOf(
                "file_name" to fileName,
                "content" to truncated,
                "size_chars" to text.length
            ),
            summary = "Read $fileName (${text.length} characters)"
        )
    }
}

// --- 18. CREATE REPOSITORY ---
class GitHubCreateRepoTool(private val service: GitHubService) : Tool {
    override val name: String = "github_create_repo"
    override val description: String =
        "Create a new repository on GitHub under the authenticated user's account, with optional description, visibility, and initial README."

    override val permission: ToolPermission = ToolPermission.WRITE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "name" to mapOf("type" to "STRING", "description" to "The name of the new repository (e.g. 'my-cool-project')"),
            "description" to mapOf("type" to "STRING", "description" to "A short description of the repository"),
            "private" to mapOf("type" to "BOOLEAN", "description" to "Whether the repository should be private (default false)"),
            "auto_init" to mapOf("type" to "BOOLEAN", "description" to "Whether to initialize the repository with an initial README (default true)")
        ),
        "required" to listOf("name")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GITHUB_KEY_NOT_SET_MSG)
        }

        val name = arguments["name"]?.toString()?.trim() ?: ""
        val description = arguments["description"]?.toString() ?: ""
        val private = (arguments["private"] as? Boolean) ?: false
        val autoInit = (arguments["auto_init"] as? Boolean) ?: true

        if (name.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "Repository name is required.")
        }

        return service.createRepository(name = name, description = description, private = private, autoInit = autoInit).fold(
            onSuccess = { res ->
                val fullName = res["full_name"]?.toString() ?: name
                val url = res["html_url"]?.toString()
                val sources = if (url != null) listOf(SourceCitation(fullName, url)) else emptyList()
                ToolResult.success(
                    data = res,
                    summary = "Created repository '$fullName'",
                    sources = sources
                )
            },
            onFailure = { ToolResult.failure("GITHUB_ERROR", it.localizedMessage ?: "Failed to create repository") }
        )
    }
}

// --- 19. DELETE REPOSITORY ---
class GitHubDeleteRepoTool(private val service: GitHubService) : Tool {
    override val name: String = "github_delete_repo"
    override val description: String =
        "Permanently delete a repository on GitHub. DESTRUCTIVE action that requires confirmation."

    override val permission: ToolPermission = ToolPermission.DESTRUCTIVE

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "OBJECT",
        "properties" to mapOf(
            "owner" to mapOf("type" to "STRING", "description" to "The repository owner username or org"),
            "repo" to mapOf("type" to "STRING", "description" to "The repository name to delete")
        ),
        "required" to listOf("owner", "repo")
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (!service.hasToken()) {
            return ToolResult.failure("CONFIG_REQUIRED", GITHUB_KEY_NOT_SET_MSG)
        }

        val owner = arguments["owner"]?.toString()?.trim() ?: ""
        val repo = arguments["repo"]?.toString()?.trim() ?: ""

        if (owner.isEmpty() || repo.isEmpty()) {
            return ToolResult.failure("INVALID_ARGUMENT", "owner and repo are required.")
        }

        return service.deleteRepository(owner = owner, repo = repo).fold(
            onSuccess = { res ->
                ToolResult.success(
                    data = res,
                    summary = "Permanently deleted repository $owner/$repo"
                )
            },
            onFailure = { ToolResult.failure("GITHUB_ERROR", it.localizedMessage ?: "Failed to delete repository") }
        )
    }
}


