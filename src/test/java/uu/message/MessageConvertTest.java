package uu.message;

import java.util.List;
import java.util.Map;

public class MessageConvertTest {
    public static void main(String[] args) {
        MessageConvert messageConvert = MessageConvert.builder().workDirectory(System.getProperty("user.dir")).build();
        // 从 text 转成 list
        List<Map.Entry<String, Map<String, String>>> list = messageConvert.toList("hi hi <image file=\"http://image.com\">");
        System.out.println(list);

        // 从 list 转成 text
        String text = messageConvert.toText(list);
        System.out.println(text);

    }
}
