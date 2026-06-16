package com.chat2api.backend.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.FileCopyUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DeepSeekPowSolver {
    private static final String TARGET_PATH = "/api/v0/chat/completion";
    private static final String WASM_FILE_NAME = "sha3_wasm_bg.7b9ca65ddd.wasm";
    private static final String[] NODE_CANDIDATES = {
        "node",
        "/usr/bin/node",
        "/usr/local/bin/node",
        "/usr/local/nvm/versions/node/current/bin/node",
        "/opt/homebrew/bin/node",
        "/snap/bin/node",
    };
    private final ObjectMapper objectMapper;
    private Path extractedWasmPath;
    private String cachedNodePath;

    public DeepSeekPowSolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private synchronized String findNode() {
        if (cachedNodePath != null) {
            return cachedNodePath;
        }
        String configured = System.getProperty("chat2api.node.path");
        if (configured != null && !configured.isBlank()) {
            cachedNodePath = configured;
            return cachedNodePath;
        }
        for (String candidate : NODE_CANDIDATES) {
            try {
                Process test = new ProcessBuilder(candidate, "--version")
                        .redirectErrorStream(true)
                        .start();
                boolean ok = test.waitFor(5, TimeUnit.SECONDS);
                if (ok && test.exitValue() == 0) {
                    cachedNodePath = candidate;
                    return cachedNodePath;
                }
            } catch (Exception ignored) {
            }
        }
        throw new IllegalStateException(
                "Node.js not found. Install Node.js or set system property -Dchat2api.node.path=/path/to/node");
    }

    public String solve(Map<String, Object> challenge) throws Exception {
        String algorithm = String.valueOf(challenge.get("algorithm"));
        if (!"DeepSeekHashV1".equals(algorithm)) {
            throw new IllegalArgumentException("Unsupported DeepSeek POW algorithm: " + algorithm);
        }
        String answer = runNodeSolver(
                String.valueOf(challenge.get("challenge")),
                String.valueOf(challenge.get("salt")),
                number(challenge.get("difficulty")).intValue(),
                number(challenge.get("expire_at")).longValue()
        );
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("algorithm", algorithm);
        response.put("challenge", challenge.get("challenge"));
        response.put("salt", challenge.get("salt"));
        response.put("answer", parseAnswer(answer));
        response.put("signature", challenge.get("signature"));
        response.put("target_path", TARGET_PATH);
        return Base64.getEncoder().encodeToString(objectMapper.writeValueAsString(response).getBytes(StandardCharsets.UTF_8));
    }

    private String runNodeSolver(String challenge, String salt, int difficulty, long expireAt) throws Exception {
        Path wasmPath = wasmPath();
        Path script = Files.createTempFile("deepseek-pow-", ".js");
        String source = "const fs=require('fs');"
                + "const path=process.argv[2];"
                + "const challenge=process.argv[3];"
                + "const salt=process.argv[4];"
                + "const difficulty=Number(process.argv[5]);"
                + "const expireAt=Number(process.argv[6]);"
                + "function enc(text,wasm){const bytes=new TextEncoder().encode(text);const ptr=wasm.__wbindgen_export_0(bytes.length,1)>>>0;new Uint8Array(wasm.memory.buffer).subarray(ptr,ptr+bytes.length).set(bytes);return [ptr,bytes.length];}"
                + "WebAssembly.instantiate(fs.readFileSync(path),{wbg:{}}).then(({instance})=>{const wasm=instance.exports;const ret=wasm.__wbindgen_add_to_stack_pointer(-16);try{const a=enc(challenge,wasm);const b=enc(`${salt}_${expireAt}_`,wasm);wasm.wasm_solve(ret,a[0],a[1],b[0],b[1],difficulty);const view=new DataView(wasm.memory.buffer);if(view.getInt32(ret,true)===0){process.exit(2);}console.log(String(view.getFloat64(ret+8,true)));}finally{wasm.__wbindgen_add_to_stack_pointer(16);}}).catch(e=>{console.error(e&&e.stack?e.stack:e);process.exit(1);});";
        Files.writeString(script, source, StandardCharsets.UTF_8);
        try {
            Process process = new ProcessBuilder(findNode(), script.toString(), wasmPath.toString(), challenge, salt, String.valueOf(difficulty), String.valueOf(expireAt))
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(Duration.ofSeconds(60).toMillis(), TimeUnit.MILLISECONDS);
            String output = new String(FileCopyUtils.copyToByteArray(process.getInputStream()), StandardCharsets.UTF_8).trim();
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("DeepSeek POW calculation timed out");
            }
            if (process.exitValue() != 0 || output.isBlank()) {
                throw new IllegalStateException("DeepSeek POW calculation failed: " + output);
            }
            return output.lines().reduce((first, second) -> second).orElse(output).trim();
        } finally {
            Files.deleteIfExists(script);
        }
    }

    private synchronized Path wasmPath() throws Exception {
        String configured = System.getProperty("chat2api.deepseek.wasm");
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path cursor = current; cursor != null; cursor = cursor.getParent()) {
            Path candidate = cursor.resolve(WASM_FILE_NAME);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            Path parentCandidate = cursor.resolve("..").normalize().resolve(WASM_FILE_NAME);
            if (Files.isRegularFile(parentCandidate)) {
                return parentCandidate;
            }
        }
        if (extractedWasmPath != null && Files.isRegularFile(extractedWasmPath)) {
            return extractedWasmPath;
        }
        try (InputStream inputStream = DeepSeekPowSolver.class.getClassLoader().getResourceAsStream(WASM_FILE_NAME)) {
            if (inputStream != null) {
                Path temp = Files.createTempFile("deepseek-pow-", ".wasm");
                Files.copy(inputStream, temp, StandardCopyOption.REPLACE_EXISTING);
                temp.toFile().deleteOnExit();
                extractedWasmPath = temp;
                return temp;
            }
        }
        throw new IllegalStateException("DeepSeek POW wasm file not found: " + WASM_FILE_NAME);
    }

    private Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private Object parseAnswer(String answer) {
        try {
            double value = Double.parseDouble(answer);
            if (Math.rint(value) == value) {
                return (long) value;
            }
            return value;
        } catch (NumberFormatException ignored) {
            return answer;
        }
    }
}
