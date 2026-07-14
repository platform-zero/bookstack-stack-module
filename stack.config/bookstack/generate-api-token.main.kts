#!/usr/bin/env kotlin

/**
 * BookStack API Token Generator
 *
 * Generates API tokens for BookStack automation services
 * Persists across obliterate operations
 */

import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun containerCommand(vararg args: String): Array<String> {
    val containerCli = System.getenv("STACK_CONTAINER_CLI")?.trim()?.takeIf { it.isNotEmpty() } ?: "podman"
    val containerName = System.getenv("STACK_BOOKSTACK_CONTAINER_NAME")?.trim()?.takeIf { it.isNotEmpty() } ?: "bookstack"
    val containerHost = when (containerCli) {
        "podman" -> System.getenv("CONTAINER_HOST")?.trim()?.takeIf { it.isNotEmpty() } ?: "unix:///run/podman/podman.sock"
        else -> null
    }
    val command = mutableListOf(containerCli)
    if (containerCli == "podman" && containerHost != null) {
        command += listOf("--remote", "--url", containerHost)
    }
    command += listOf("exec", containerName)
    command += args
    return command.toTypedArray()
}

fun run(vararg cmd: String): String {
    val pb = ProcessBuilder(*cmd).redirectErrorStream(true)
    val p = pb.start()
    val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
    val code = p.waitFor()
    if (code != 0) {
        println("[ERROR] Command failed: ${cmd.joinToString(" ")}")
        println(out)
        kotlin.system.exitProcess(1)
    }
    return out.trim()
}

fun generateToken(): String {
    val random = SecureRandom()
    val bytes = ByteArray(32)
    random.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

fun tokenExpiry(): String {
    val ttlDays = System.getenv("BOOKSTACK_API_TOKEN_TTL_DAYS")
        ?.toLongOrNull()
        ?.takeIf { it > 0 }
        ?: 90L
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    return Instant.now()
        .plus(Duration.ofDays(ttlDays))
        .atOffset(ZoneOffset.UTC)
        .format(formatter)
}

fun main() {
    println("[INFO] Generating BookStack API token...")

    // Generate token credentials
    val tokenId = "webservices-automation-${System.currentTimeMillis()}"
    val tokenSecret = generateToken()

    println("[INFO] Token identifier generated")
    println("[INFO] Generating secret...")
    val expiresAt = tokenExpiry()
    println("[INFO] Token expires at: $expiresAt UTC")

    // Ensure a dedicated non-admin automation user is used for API writes.
    val ensureAutomationUserCmd = """
        ${'$'}roleId = \Illuminate\Support\Facades\DB::table('roles')->where('system_name', 'automation')->value('id');
        if (!${'$'}roleId) { throw new RuntimeException('BookStack automation role is missing; run 50-configure-permissions.sh first'); }
        ${'$'}userId = \Illuminate\Support\Facades\DB::table('users')->where('email', 'webservices-automation@localhost')->value('id');
        if (!${'$'}userId) {
            ${'$'}userId = \Illuminate\Support\Facades\DB::table('users')->insertGetId([
                'name' => 'webservices Automation',
                'email' => 'webservices-automation@localhost',
                'password' => '',
                'remember_token' => null,
                'created_at' => now(),
                'updated_at' => now(),
                'email_confirmed' => 1,
                'image_id' => 0,
                'external_auth_id' => '',
                'slug' => 'webservices-automation',
                'system_name' => null,
            ]);
        }
        \Illuminate\Support\Facades\DB::table('role_user')->insertOrIgnore(['user_id' => ${'$'}userId, 'role_id' => ${'$'}roleId]);
        echo ${'$'}userId;
    """.trimIndent().replace("\n", " ")
    val userId = run(
        *containerCommand(
            "php", "/app/www/artisan", "tinker", "--execute=$ensureAutomationUserCmd"
        )
    ).trim()

    println("[INFO] Using automation user ID: $userId")

    // Check if token already exists
    val existingCheck = run(
        *containerCommand(
            "php", "/app/www/artisan", "tinker", "--execute=echo BookStack\\Api\\ApiToken::where('name', 'webservices Automation')->count();"
        )
    ).trim()

    if (existingCheck != "0") {
        println("[WARN] Token 'webservices Automation' already exists, removing old one...")
        run(
            *containerCommand(
                "php", "/app/www/artisan", "tinker", "--execute=BookStack\\Api\\ApiToken::where('name', 'webservices Automation')->delete();"
            )
        )
    }

    val createCmd = "\$user = BookStack\\Users\\Models\\User::find($userId); " +
        "\$token = new BookStack\\Api\\ApiToken(); " +
        "\$token->user_id = \$user->id; " +
        "\$token->name = 'webservices Automation'; " +
        "\$token->token_id = '$tokenId'; " +
        "\$token->secret = \\Illuminate\\Support\\Facades\\Hash::make('$tokenSecret'); " +
        "\$token->expires_at = '$expiresAt'; " +
        "\$token->save(); " +
        "echo 'Token created successfully';"

    val result = run(
        *containerCommand(
            "php", "/app/www/artisan", "tinker", "--execute=$createCmd"
        )
    )

    println("[SUCCESS] $result")
    println()
    println("=".repeat(80))
    println("Add these to your runtime env file (for example dist/runtime/stack.env):")
    println("=".repeat(80))
    println("BOOKSTACK_API_TOKEN_ID=$tokenId")
    println("BOOKSTACK_API_TOKEN_SECRET=$tokenSecret")
    println()
    println("Or export them now:")
    println("export BOOKSTACK_API_TOKEN_ID=$tokenId")
    println("export BOOKSTACK_API_TOKEN_SECRET=$tokenSecret")
    println("=".repeat(80))
}

main()
