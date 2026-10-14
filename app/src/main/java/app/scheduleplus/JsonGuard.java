package app.scheduleplus;

/** Strict, bounded syntax validation before Android's permissive recursive JSON parser.
 * Rejects comments, single quotes, unquoted values, malformed escapes and trailing data.
 * Recursion is checked before descending; validation allocates no parsed object tree.
 */
public final class JsonGuard {
    private final String input;
    private final int maxDepth;
    private int index,values;
    private JsonGuard(String input,int maxDepth){this.input=input;this.maxDepth=maxDepth;}
    public static void validate(String input,int maxDepth){
        JsonGuard parser=new JsonGuard(input,maxDepth);parser.value(0);parser.whitespace();
        if(parser.index!=input.length())parser.fail("JSON 末尾存在多余内容");
    }
    private void value(int depth){
        if(++values>100000)fail("备份结构过于复杂");
        whitespace();if(index>=input.length())fail("JSON 文件不完整");
        char ch=input.charAt(index);
        if(ch=='{' || ch=='['){
            if(depth>=maxDepth)fail("备份嵌套层级过深");index++;
            if(ch=='{')object(depth+1);else array(depth+1);
        }else if(ch=='"')string();
        else if(ch=='t')literal("true");else if(ch=='f')literal("false");else if(ch=='n')literal("null");
        else if(ch=='-' || digit(ch))number();else fail("备份必须使用标准 JSON 语法");
    }
    private void object(int depth){
        whitespace();if(take('}'))return;
        do{whitespace();if(index>=input.length() || input.charAt(index)!='"')fail("JSON 字段名必须用双引号");string();whitespace();expect(':');value(depth);whitespace();if(take('}'))return;expect(',');}while(true);
    }
    private void array(int depth){
        whitespace();if(take(']'))return;
        do{value(depth);whitespace();if(take(']'))return;expect(',');}while(true);
    }
    private void string(){
        expect('"');
        while(index<input.length()){
            char ch=input.charAt(index++);if(ch=='"')return;if(ch<0x20)fail("JSON 字符串包含未转义的控制字符");
            if(ch=='\\'){
                if(index>=input.length())fail("JSON 转义不完整");char escaped=input.charAt(index++);
                if(escaped=='u'){
                    for(int i=0;i<4;i++){if(index>=input.length() || !hex(input.charAt(index++)))fail("JSON Unicode 转义无效");}
                }else if("\"\\/bfnrt".indexOf(escaped)<0)fail("JSON 转义无效");
            }
        }
        fail("JSON 字符串不完整");
    }
    private void number(){
        take('-');if(index>=input.length())fail("JSON 数字无效");
        if(take('0')){if(index<input.length() && digit(input.charAt(index)))fail("JSON 数字不能带前导零");}
        else{if(input.charAt(index)<'1' || input.charAt(index)>'9')fail("JSON 数字无效");digits();}
        if(take('.'))requiredDigits();
        if(take('e') || take('E')){if(!take('+'))take('-');requiredDigits();}
    }
    private void requiredDigits(){if(index>=input.length() || !digit(input.charAt(index)))fail("JSON 数字不完整");digits();}
    private void digits(){while(index<input.length() && digit(input.charAt(index)))index++;}
    private void literal(String token){if(!input.startsWith(token,index))fail("JSON 字面量无效");index+=token.length();}
    private void whitespace(){while(index<input.length() && " \t\r\n".indexOf(input.charAt(index))>=0)index++;}
    private boolean take(char ch){if(index<input.length() && input.charAt(index)==ch){index++;return true;}return false;}
    private void expect(char ch){if(!take(ch))fail("JSON 结构无效");}
    private static boolean digit(char ch){return ch>='0' && ch<='9';}
    private static boolean hex(char ch){return digit(ch) || ch>='a' && ch<='f' || ch>='A' && ch<='F';}
    private void fail(String message){throw new IllegalArgumentException(message+"（位置 "+(index+1)+"）");}
}
