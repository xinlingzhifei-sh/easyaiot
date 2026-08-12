#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
将Markdown转换为公众号可用的HTML格式
"""

import re

def convert_markdown_to_wechat_html(md_content):
    """将Markdown转换为公众号HTML格式"""
    
    # 标题图标映射
    title_icons = {
        "引言": "💡",
        "项目概述": "📋",
        "设计哲学": "🎯",
        "平台定位": "📍",
        "核心价值": "✨",
        "技术架构": "🏗️",
        "模块化设计理念": "🧩",
        "数据流转架构": "🔄",
        "存储方案": "💾",
        "核心AI能力": "🤖",
        "全面的AI技术栈": "🔧",
        "革命性的零样本标注技术": "🚀",
        "多场景预训练模型": "📦",
        "IoT能力": "🌐",
        "完整的设备生命周期管理": "📱",
        "强大的规则引擎": "⚙️",
        "数据智能分析": "📊",
        "部署灵活性": "☁️",
        "独立部署优势": "🔀",
        "一键部署方案": "⚡",
        "应用场景适配": "🎨",
        "核心优势": "⭐",
        "多语言混编架构": "🔤",
        "零样本标注技术": "🎯",
        "灵活部署": "🌍",
        "丰富生态支持": "🌳",
        "持续迭代优化": "🔄",
        "应用场景": "🎬",
        "人群密度管控": "👥",
        "周界防护": "🛡️",
        "跌倒检测": "⚠️",
        "异常逗留识别": "👀",
        "肢体冲突预警": "⚔️",
        "非法闯入检测": "🚫",
        "公共场所控烟": "🚭",
        "人流统计管控": "📈",
        "区域越界预警": "🚧",
        "环境安全检查": "🔍",
        "火灾早预警": "🔥",
        "扩展应用领域": "🔮",
        "系统展示": "🖼️",
        "核心功能界面展示": "💻",
        "技术实现": "💻",
        "设备控制核心逻辑": "🎮",
        "安全认证体系": "🔐",
        "AI模型管理": "🧠",
        "高性能任务处理": "⚡",
        "功能介绍": "📖",
        "设备管理模块": "📱",
        "流媒体管理模块": "🎥",
        "数据标注模块": "✏️",
        "模型训练与管理模块": "🎓",
        "AI智能分析模块": "🔬",
        "规则引擎模块": "⚙️",
        "系统管理模块": "🛠️",
        "数据统计与分析模块": "📊",
        "部署安装": "🚀",
        "部署要求": "📋",
        "部署优势": "✅",
        "社区与开源": "❤️",
        "我们的承诺": "🤝",
        "加入我们": "🌟",
        "演示环境与支持": "🌐",
        "在线演示": "💻",
        "结语": "🎉",
        "联系方式": "📞",
    }
    
    html_lines = []
    html_lines.append('<!DOCTYPE html>')
    html_lines.append('<html>')
    html_lines.append('<head>')
    html_lines.append('<meta charset="UTF-8">')
    html_lines.append('<meta name="viewport" content="width=device-width, initial-scale=1.0">')
    html_lines.append('<style>')
    html_lines.append('body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif; line-height: 1.8; color: #333; max-width: 800px; margin: 0 auto; padding: 20px; }')
    html_lines.append('h1 { font-size: 24px; font-weight: bold; color: #1a1a1a; margin: 30px 0 20px; padding-bottom: 10px; border-bottom: 2px solid #e0e0e0; }')
    html_lines.append('h2 { font-size: 20px; font-weight: bold; color: #2c3e50; margin: 25px 0 15px; }')
    html_lines.append('h3 { font-size: 18px; font-weight: bold; color: #34495e; margin: 20px 0 12px; }')
    html_lines.append('h4 { font-size: 16px; font-weight: bold; color: #555; margin: 15px 0 10px; }')
    html_lines.append('p { margin: 12px 0; text-align: justify; }')
    html_lines.append('blockquote { border-left: 4px solid #3498db; padding-left: 15px; margin: 15px 0; color: #666; font-style: italic; }')
    html_lines.append('ul, ol { margin: 15px 0; padding-left: 30px; }')
    html_lines.append('li { margin: 8px 0; }')
    html_lines.append('strong { color: #e74c3c; font-weight: bold; }')
    html_lines.append('code { background-color: #f4f4f4; padding: 2px 6px; border-radius: 3px; font-family: "Courier New", monospace; font-size: 14px; }')
    html_lines.append('pre { background-color: #2d2d2d; color: #f8f8f2; padding: 15px; border-radius: 5px; overflow-x: auto; margin: 15px 0; }')
    html_lines.append('pre code { background-color: transparent; padding: 0; color: inherit; }')
    html_lines.append('hr { border: none; border-top: 1px solid #e0e0e0; margin: 30px 0; }')
    html_lines.append('a { color: #3498db; text-decoration: none; }')
    html_lines.append('a:hover { text-decoration: underline; }')
    html_lines.append('</style>')
    html_lines.append('</head>')
    html_lines.append('<body>')
    
    lines = md_content.split('\n')
    i = 0
    in_code_block = False
    code_language = ''
    
    while i < len(lines):
        line = lines[i]
        
        # 处理代码块
        if line.strip().startswith('```'):
            if not in_code_block:
                in_code_block = True
                code_language = line.strip()[3:].strip()
                html_lines.append(f'<pre><code class="language-{code_language}">')
            else:
                in_code_block = False
                html_lines.append('</code></pre>')
            i += 1
            continue
        
        if in_code_block:
            html_lines.append(escape_html(line) + '\n')
            i += 1
            continue
        
        # 处理标题
        if line.startswith('# '):
            title = line[2:].strip()
            html_lines.append(f'<h1>{title}</h1>')
        elif line.startswith('## '):
            title = line[3:].strip()
            # 提取标题关键词，添加图标
            icon = get_icon_for_title(title, title_icons)
            html_lines.append(f'<h2>{icon} {title}</h2>')
        elif line.startswith('### '):
            title = line[4:].strip()
            icon = get_icon_for_title(title, title_icons)
            html_lines.append(f'<h3>{icon} {title}</h3>')
        elif line.startswith('#### '):
            title = line[5:].strip()
            icon = get_icon_for_title(title, title_icons)
            html_lines.append(f'<h4>{icon} {title}</h4>')
        # 处理引用
        elif line.startswith('> '):
            quote = line[2:].strip()
            quote = process_inline_formatting(quote)
            html_lines.append(f'<blockquote>{quote}</blockquote>')
        # 处理分隔线
        elif line.strip() == '---':
            html_lines.append('<hr>')
        # 处理列表
        elif line.strip().startswith('- '):
            html_lines.append('<ul>')
            while i < len(lines) and lines[i].strip().startswith('- '):
                item = lines[i][2:].strip()
                item = process_inline_formatting(item)
                html_lines.append(f'<li>{item}</li>')
                i += 1
            html_lines.append('</ul>')
            continue
        elif re.match(r'^\d+\.\s', line):
            html_lines.append('<ol>')
            while i < len(lines) and re.match(r'^\d+\.\s', lines[i]):
                item = re.sub(r'^\d+\.\s', '', lines[i]).strip()
                item = process_inline_formatting(item)
                html_lines.append(f'<li>{item}</li>')
                i += 1
            html_lines.append('</ol>')
            continue
        # 处理普通段落
        elif line.strip():
            content = process_inline_formatting(line.strip())
            html_lines.append(f'<p>{content}</p>')
        else:
            html_lines.append('<br>')
        
        i += 1
    
    html_lines.append('</body>')
    html_lines.append('</html>')
    
    return '\n'.join(html_lines)

def get_icon_for_title(title, title_icons):
    """为标题获取合适的图标"""
    # 先尝试完整匹配
    if title in title_icons:
        return title_icons[title]
    
    # 尝试部分匹配
    for key, icon in title_icons.items():
        if key in title:
            return icon
    
    # 根据标题内容智能匹配
    if '模块' in title:
        return '📦'
    elif '管理' in title:
        return '🛠️'
    elif '技术' in title or '架构' in title:
        return '🏗️'
    elif '部署' in title or '安装' in title:
        return '🚀'
    elif '场景' in title or '应用' in title:
        return '🎬'
    elif '能力' in title or '功能' in title:
        return '✨'
    elif '分析' in title or '统计' in title:
        return '📊'
    elif '训练' in title or '模型' in title:
        return '🧠'
    elif '设备' in title:
        return '📱'
    elif '视频' in title or '流媒体' in title:
        return '🎥'
    elif '数据' in title:
        return '💾'
    elif '安全' in title:
        return '🔐'
    elif '规则' in title or '引擎' in title:
        return '⚙️'
    elif '演示' in title or '环境' in title:
        return '🌐'
    elif '社区' in title or '开源' in title:
        return '❤️'
    elif '联系' in title:
        return '📞'
    elif '结语' in title:
        return '🎉'
    else:
        return '📌'

def process_inline_formatting(text):
    """处理行内格式（加粗、代码、链接）"""
    # 处理加粗 **text**
    text = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', text)
    # 处理代码 `code`
    text = re.sub(r'`([^`]+)`', r'<code>\1</code>', text)
    # 处理链接 [text](url)
    text = re.sub(r'\[([^\]]+)\]\(([^\)]+)\)', r'<a href="\2">\1</a>', text)
    return text

def escape_html(text):
    """转义HTML特殊字符"""
    text = text.replace('&', '&amp;')
    text = text.replace('<', '&lt;')
    text = text.replace('>', '&gt;')
    return text

if __name__ == '__main__':
    # 读取markdown文件
    with open('/projects/yfeieye/.doc/宣传文章/项目宣传2.md', 'r', encoding='utf-8') as f:
        md_content = f.read()
    
    # 转换为HTML
    html_content = convert_markdown_to_wechat_html(md_content)
    
    # 保存HTML文件
    with open('/projects/yfeieye/.doc/宣传文章/doc/项目宣传2_公众号版.html', 'w', encoding='utf-8') as f:
        f.write(html_content)
    
    print("转换完成！HTML文件已保存到: .doc/宣传文章/doc/项目宣传2_公众号版.html")
