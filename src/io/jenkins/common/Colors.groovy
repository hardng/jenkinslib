// src/io/jenkins/common/Colors.groovy
package io.jenkins.common

/**
 * ANSI
 * 
 * 用法：
 *   import static com.example.utils.Colors.*
 *   echo "${BLUE}文字${RESET}"
 * 
 * 需要安装plugin ansiColor
 */
class Colors implements Serializable {

    private static final long serialVersionUID = 1L

    // 基本样式
    public static final String RESET     = '\u001B[0m'
    public static final String BOLD      = '\u001B[1m'
    public static final String DIM       = '\u001B[2m'
    public static final String UNDERLINE = '\u001B[4m'
    public static final String BLINK     = '\u001B[5m'
    public static final String REVERSED  = '\u001B[7m'
    public static final String HIDDEN    = '\u001B[8m'

    // 前景色
    public static final String BLACK  = '\u001B[30m'
    public static final String RED    = '\u001B[31m'
    public static final String GREEN  = '\u001B[32m'
    public static final String YELLOW = '\u001B[33m'
    public static final String BLUE   = '\u001B[34m'
    public static final String PURPLE = '\u001B[35m'
    public static final String CYAN   = '\u001B[36m'
    public static final String WHITE  = '\u001B[37m'

    public static final String BRIGHT_BLACK  = '\u001B[90m'
    public static final String BRIGHT_RED    = '\u001B[91m'
    public static final String BRIGHT_GREEN  = '\u001B[92m'
    public static final String BRIGHT_YELLOW = '\u001B[93m'
    public static final String BRIGHT_BLUE   = '\u001B[94m'
    public static final String BRIGHT_PURPLE = '\u001B[95m'
    public static final String BRIGHT_CYAN   = '\u001B[96m'
    public static final String BRIGHT_WHITE  = '\u001B[97m'

    // 背景色
    public static final String BG_BLACK  = '\u001B[40m'
    public static final String BG_RED    = '\u001B[41m'
    public static final String BG_GREEN  = '\u001B[42m'
    public static final String BG_YELLOW = '\u001B[43m'
    public static final String BG_BLUE   = '\u001B[44m'
    public static final String BG_PURPLE = '\u001B[45m'
    public static final String BG_CYAN   = '\u001B[46m'
    public static final String BG_WHITE  = '\u001B[47m'

    public static final String BG_BRIGHT_BLACK  = '\u001B[100m'
    public static final String BG_BRIGHT_RED    = '\u001B[101m'
    public static final String BG_BRIGHT_GREEN  = '\u001B[102m'
    public static final String BG_BRIGHT_YELLOW = '\u001B[103m'
    public static final String BG_BRIGHT_BLUE   = '\u001B[104m'
    public static final String BG_BRIGHT_PURPLE = '\u001B[105m'
    public static final String BG_BRIGHT_CYAN   = '\u001B[106m'
    public static final String BG_BRIGHT_WHITE  = '\u001B[107m'
}