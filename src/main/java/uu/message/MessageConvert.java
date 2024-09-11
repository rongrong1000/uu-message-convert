package uu.message;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class MessageConvert {
    // 路径分隔符
    private String separator = "/";
    // 是否把文件转换成 base64, 指本地文件
    private boolean fileConvertToBase64;
    // 工作路径, @是工作路径的别名, 别名只在路径最前才会生效
    private String workDirectory = System.getProperty("user.dir");

    public static MessageConvert builder() {
        return new MessageConvert();
    }

    private MessageConvert() {
    }

    public MessageConvert build() {
        return this;
    }

    public MessageConvert separator(String separator) {
        this.separator = separator;
        return this;
    }

    public MessageConvert fileConvertToBase64(boolean fileConvertToBase64) {
        this.fileConvertToBase64 = fileConvertToBase64;
        return this;
    }

    public MessageConvert workDirectory(String workDirectory) {
        Objects.requireNonNull(workDirectory);
        this.workDirectory = workDirectory;
        return this;
    }

    public List<Map.Entry<String, Map<String, String>>> toList(String s, String root) {
        if (root == null) {
            root = workDirectory;
        }
        List<Map.Entry<String, Map<String, String>>> list = new ArrayList<>();
        boolean spaceValue = false; // uu码 value 部分 是否是

        StringBuilder content = new StringBuilder();
        StringBuilder internalCodeText = new StringBuilder();
        StringBuilder escapeCode = new StringBuilder();
        boolean internalCode = false;    // 内部码
        boolean quotationMark = false;
        boolean escape = false; // 转义&___;
        for (char c : s.toCharArray()) {
            if (internalCode) {
                if (quotationMark) {
                    quotationMark = false;
                    internalCodeText.append(c);
                } else if (c == '\\' && spaceValue) {
                    quotationMark = true;
                    internalCodeText.append(c);
                } else if (c == '"') {
                    spaceValue = !spaceValue;
                    internalCodeText.append(c);
                } else if (c == '>' && !spaceValue) {
                    try {
                        Map.Entry<String, Map<String, String>> entry = parseCode(internalCodeText.toString());
                        // 额外处理路径变量 (重新替换
                        String file = entry.getValue().get("file");
                        if (file == null) {
                            // nothing
                        } else if (file.startsWith("http://") || file.startsWith("https://")) {
                            // entry.getValue().put("file", file);
                        } else if (file.startsWith("base64://")) {
                            // entry.getValue().put("file", file);
                        } else if (file.length() > 512) {
                            // entry.getValue().put("file", file);
                        } else {
                            // 一律当本地文件
                            try {
                                String path = file.startsWith("file:///") ? file.substring("file:///".length()) : file;

                                // 处理别名
                                if (path.startsWith("@/") || path.startsWith("@\\")) {
                                    path = root + path.substring(1);
                                }
                                // 不是绝对路径, 补全成绝对路径
                                if (!isAbsolutePath(path)) {
                                    path = root + file;
                                }
                                // 把 /.././ 等处理成绝对路径 不支持
                                if (fileConvertToBase64) {
                                    entry.getValue().put("file", "base64://" + readFileToBase64(new File(path)));
                                } else {
                                    entry.getValue().put("file", "file:///" + path);
                                }
                            } catch (Exception ee) {

                            }
                        }
                        list.add(entry);
                    } catch (Exception e) {
                        // nothing
                    }
                    internalCodeText.setLength(0);
                    internalCode = false;
                } else {
                    internalCodeText.append(c);
                }
            } else if (escape) {
                if (c == ';') {
                    String code = escapeCode.toString();
                    if ("lt".equals(code) || "#60".equals(code)) {
                        content.append('<');
                    } else if ("gt".equals(code) || "#62".equals(code)) {
                        content.append('>');
                    } else if ("lsb".equals(code) || "#91".equals(code)) {
                        content.append('[');
                    } else if ("rsb".equals(code) || "#93".equals(code)) {
                        content.append(']');
                    } else if ("amp".equals(code) || "#38".equals(code)) {
                        content.append('&');
                    } else {
                        content.append('&');
                        content.append(code);
                        content.append(';');
                    }
                    escape = false;
                } else {
                    escapeCode.append(c);
                }
            } else if (c == '&') {
                escape = true;
                escapeCode.setLength(0);
            } else if (c == '<') {
                internalCode = true;
                if (!content.isEmpty()) {
                    Map<String, String> map = new HashMap<>();
                    map.put("value", content.toString());
                    content.setLength(0);
                    list.add(new AbstractMap.SimpleEntry<>("text", map));
                }
            } else {
                content.append(c);
            }
        }
        if (!content.isEmpty()) {
            Map<String, String> map = new HashMap<>();
            map.put("value", content.toString());
            list.add(new AbstractMap.SimpleEntry<>("text", map));
        }
        return list;
    }

    public List<Map.Entry<String, Map<String, String>>> toList(String s) {
        return toList(s, null);
    }

    public String toText(List<Map.Entry<String, Map<String, String>>> list) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Map<String, String>> entry : list) {
            if ("text".equals(entry.getKey())) {
                sb.append(entry.getValue().get("value"));
            } else {
                sb.append('<');
                sb.append(entry.getKey());
                for (Map.Entry<String, String> obj : entry.getValue().entrySet()) {
                    sb.append(" ");
                    sb.append(obj.getKey());
                    if (obj.getValue() != null) {
                        sb.append('=');
                        sb.append('"');
                        sb.append(escape(obj.getValue()));
                        sb.append('"');
                    }
                }
                sb.append('>');
            }
        }
        return sb.toString();
    }

    public Map.Entry<String, Map<String, String>> parseCode(String s) {
        Map<String, String> map = new LinkedHashMap<>();
        StringBuilder name = new StringBuilder();
        int nameIndex = 0;
        for (char c : s.toCharArray()) {
            nameIndex++;
            if (isWhiteSpace(c)) {
                if (!name.isEmpty()) {
                    break;
                }
            } if (c == '=' || c == '"') {
                throw new RuntimeException();
            } else {
                name.append(c);
            }
        }
        if (name.isEmpty()) {
            throw new RuntimeException();
        }
        s = s.substring(nameIndex);
        boolean scanKey = true; // 状态机, 扫描 key
        boolean scanEqual = false; // 状态机, 扫描 =
        boolean scanValue = false; // 状态机, 扫描 value
        StringBuilder key = new StringBuilder();
        StringBuilder value = new StringBuilder();
        boolean inDomain = false; // 是否进入 value 域内
        boolean escape = false; // 只有在 scanValue = true 生效, 表示下一个字符需要转义
        // StringBuilder temp = new StringBuilder();

        for (char c : s.toCharArray()) {
            // temp.append(c);
            if (scanKey) {
                if (isWhiteSpace(c)) {
                    if (key.isEmpty()) {
                        continue;
                    } else {
                        scanKey = false;
                        scanEqual = true;
                    }
                } else if (c == '=') {
                    scanKey = false;
                    scanValue = true;
                } else if (c == '"') {
                    throw new RuntimeException();
                } else {
                    key.append(c);
                }
            } else if (scanEqual) {
                if (c == '=') {
                    scanEqual = false;
                    scanValue = true;
                } else if (isWhiteSpace(c)) {
                    continue;
                } else if (c == '"') {
                    throw new RuntimeException();
                } else {
                    // 没有 value
                    scanKey = true;
                    scanEqual = false;
                    map.put(key.toString(), null);
                    key.setLength(0);
                    key.append(c);
                }
            } else if (scanValue) {
                if (inDomain) {
                    if (escape) {
                        if (c == 'n') {
                            value.append('\n');
                        } else if (c == 'r') {
                            value.append('\r');
                        } else if (c == 't') {
                            value.append('\t');
                        } else {
                            value.append(c);
                        }
                        escape = false;
                    } else if (c == '\\') {
                        escape = true;
                    } else if (c == '"') {
                        // 结束
                        map.put(key.toString(), value.toString());
                        key.setLength(0);
                        value.setLength(0);
                        inDomain = false;
                        scanKey = true;
                        scanValue = false;
                    } else {
                        value.append(c);
                    }
                } else {
                    if (c == '"') {
                        inDomain = true;
                    } else if (isWhiteSpace(c)) {
                        continue;
                    } else {
                        throw new RuntimeException();
                    }
                }

            }
        }
        if (!scanValue && !key.isEmpty()) {
            map.put(key.toString(), null);
        } else if (scanValue) {
            throw new RuntimeException();
        }
        return new AbstractMap.SimpleEntry<>(name.toString(), map);
    }

    private static boolean isWhiteSpace(char c) {
        return c == ' ' || c == '\n' || c == '\t' || c == '\r';
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '\\') {
                sb.append("\\\\");
            } else if (c == '\"') {
                sb.append("\\\"");
            } else if (c == '\t') {
                sb.append("\\t");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\b') {
                sb.append("\\b");
            } else if (c == '\n') {
                sb.append("\\n");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // 是否是绝对路径
    private boolean isAbsolutePath(String path) {
        return path.startsWith("/") ||
                path.startsWith("\\") ||
                path.contains(":\\") ||
                path.contains(":/");
    }

    private static String readFileToBase64(File file) {
        Objects.requireNonNull(file, "file is null");
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
