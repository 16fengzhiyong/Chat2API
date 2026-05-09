package com.chat2api.backend.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DeepSeekPowSolver {
    private static final String TARGET_PATH = "/api/v0/chat/completion";
    private final ObjectMapper objectMapper;

    public DeepSeekPowSolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
            Process process = new ProcessBuilder("node", script.toString(), wasmPath.toString(), challenge, salt, String.valueOf(difficulty), String.valueOf(expireAt))
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

    private Path wasmPath() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path cursor = current; cursor != null; cursor = cursor.getParent()) {
            Path candidate = cursor.resolve("sha3_wasm_bg.7b9ca65ddd.wasm");
            if (Files.exists(candidate)) {
                return candidate;
            }
            Path parentCandidate = cursor.resolve("..").normalize().resolve("sha3_wasm_bg.7b9ca65ddd.wasm");
            if (Files.exists(parentCandidate)) {
                return parentCandidate;
            }
        }
        String configured = System.getProperty("chat2api.deepseek.wasm");
        if (configured != null && !configured.isBlank() && new File(configured).exists()) {
            return Path.of(configured);
        }
        throw new IllegalStateException("DeepSeek POW wasm file not found: sha3_wasm_bg.7b9ca65ddd.wasm");
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
