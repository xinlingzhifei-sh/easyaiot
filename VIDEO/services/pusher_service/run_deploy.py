"""
推送器服务模板工程
用于推送视频流和事件告警

@author reese
@email reese
"""
import os
import sys
import time
import threading
import logging
import socket
import atexit
import signal
import argparse
import json
import requests
from datetime import datetime
from logging.handlers import TimedRotatingFileHandler
from flask import Flask, request, jsonify
from flask_cors import CORS
from dotenv import load_dotenv
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, scoped_session

# 添加VIDEO模块路径以便导入模型
video_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, video_root)

# 导入VIDEO模块的模型
from models import db, AlgorithmTask, Pusher

# ============================================
# 全局异常处理器
# ============================================
def handle_exception(exc_type, exc_value, exc_traceback):
    """全局异常处理器"""
    if issubclass(exc_type, KeyboardInterrupt):
        sys.__excepthook__(exc_type, exc_value, exc_traceback)
        return
    
    import traceback
    error_msg = f"❌ [PUSHER] 未捕获的异常: {exc_type.__name__}: {exc_value}"
    print(error_msg, file=sys.stderr)
    print("=" * 60, file=sys.stderr)
    traceback.print_exception(exc_type, exc_value, exc_traceback, file=sys.stderr)
    print("=" * 60, file=sys.stderr)
    
    try:
        logger = logging.getLogger(__name__)
        logger.error(f"未捕获的异常: {exc_type.__name__}: {exc_value}")
        logger.error(traceback.format_exception(exc_type, exc_value, exc_traceback))
    except:
        pass

sys.excepthook = handle_exception

# ============================================
# 自定义日志处理器
# ============================================
class DailyRotatingFileHandler(logging.FileHandler):
    """按日期自动切换的日志文件处理器"""
    
    def __init__(self, log_dir, filename_pattern='%Y-%m-%d.log', encoding='utf-8'):
        self.log_dir = log_dir
        self.filename_pattern = filename_pattern
        self.current_date = datetime.now().date()
        self.current_file_path = None
        self._update_file_path()
        super().__init__(self.current_file_path, encoding=encoding)
    
    def _update_file_path(self):
        """更新当前日志文件路径"""
        today = datetime.now().date()
        if today != self.current_date or self.current_file_path is None:
            self.current_date = today
            filename = datetime.now().strftime(self.filename_pattern)
            self.current_file_path = os.path.join(self.log_dir, filename)
    
    def emit(self, record):
        """发送日志记录，如果日期变化则切换文件"""
        if datetime.now().date() != self.current_date:
            self.close()
            self._update_file_path()
            self.baseFilename = self.current_file_path
            if self.stream:
                self.stream.close()
                self.stream = None
            self.stream = self._open()
        
        super().emit(record)

# ============================================
# 环境变量和系统配置初始化
# ============================================
env_file = '.env'
if os.path.exists(env_file):
    load_dotenv(env_file, override=True)
    print(f"✅ 已加载配置文件: {env_file}", file=sys.stderr)
else:
    print(f"⚠️  配置文件 {env_file} 不存在，使用系统环境变量", file=sys.stderr)

app = Flask(__name__)
CORS(app)

# 配置日志
logging.getLogger('werkzeug').setLevel(logging.WARNING)
logging.getLogger('flask').setLevel(logging.WARNING)

# 获取服务ID
service_id = os.getenv('SERVICE_ID', 'unknown')
task_id = os.getenv('TASK_ID')  # 算法任务ID
pusher_id = os.getenv('PUSHER_ID')  # 推送器ID

# 日志目录
log_path = os.getenv('LOG_PATH')
if log_path:
    service_log_dir = log_path
else:
    video_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    logs_base_dir = os.path.join(video_root, 'logs')
    service_log_dir = os.path.join(logs_base_dir, f'pusher_{service_id}')
os.makedirs(service_log_dir, exist_ok=True)

# 创建日志格式
log_format = '[PUSHER] %(asctime)s - %(name)s - %(levelname)s - %(message)s'
formatter = logging.Formatter(log_format)

# 创建根logger
root_logger = logging.getLogger()
root_logger.setLevel(logging.INFO)
root_logger.handlers.clear()

# 创建文件handler
file_handler = DailyRotatingFileHandler(service_log_dir, filename_pattern='%Y-%m-%d.log', encoding='utf-8')
file_handler.setLevel(logging.INFO)
file_handler.setFormatter(formatter)
root_logger.addHandler(file_handler)

# 同时输出到stderr
console_handler = logging.StreamHandler(sys.stderr)
console_handler.setLevel(logging.INFO)
console_handler.setFormatter(formatter)
root_logger.addHandler(console_handler)

logger = logging.getLogger(__name__)
logger.info("=" * 60)
logger.info("🚀 推送器服务启动")
logger.info(f"任务ID: {task_id}")
logger.info(f"推送器ID: {pusher_id}")
logger.info(f"日志目录: {service_log_dir}")
logger.info("=" * 60)

# 全局变量
db_session = None
task = None
pusher = None
running = False
stop_event = threading.Event()
video_service_api = None
server_ip = None
port = None
process_id = os.getpid()

# ============================================
# 数据库连接
# ============================================
def init_database():
    """初始化数据库连接"""
    global db_session
    
    database_url = os.getenv('DATABASE_URL')
    if not database_url:
        raise ValueError("DATABASE_URL环境变量未设置")
    
    # 创建数据库引擎
    engine = create_engine(database_url, pool_pre_ping=True, pool_recycle=3600)
    session_factory = sessionmaker(bind=engine)
    db_session = scoped_session(session_factory)
    
    logger.info("数据库连接初始化成功")


# ============================================
# 心跳上报
# ============================================
def get_video_service_api():
    """获取VIDEO服务API地址"""
    api_url = os.getenv('VIDEO_SERVICE_API')
    if api_url:
        return api_url.rstrip('/')
    default_port = os.getenv('VIDEO_SERVICE_PORT', '6000')
    return f'http://localhost:{default_port}'


def send_heartbeat():
    """向VIDEO模块发送心跳"""
    global video_service_api, server_ip, port, process_id, pusher_id, task_id, service_log_dir
    
    time.sleep(2)
    
    while not stop_event.is_set():
        try:
            if not video_service_api:
                video_service_api = get_video_service_api()
                if not video_service_api:
                    logger.warning("VIDEO服务地址未获取到，等待10秒后重试...")
                    time.sleep(10)
                    continue
            
            if not server_ip:
                try:
                    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                    s.connect(('8.8.8.8', 80))
                    server_ip = s.getsockname()[0]
                    s.close()
                except:
                    server_ip = '127.0.0.1'
            
            if not port:
                port = int(os.getenv('PORT', 8003))
            
            if not pusher_id:
                logger.warning("PUSHER_ID 环境变量未设置，无法发送心跳")
                time.sleep(60)
                continue
            
            try:
                pusher_id_int = int(pusher_id)
            except (ValueError, TypeError):
                logger.error(f"PUSHER_ID 无效: {pusher_id}，必须是数字")
                time.sleep(60)
                continue
            
            heartbeat_data = {
                'pusher_id': pusher_id_int,
                'server_ip': server_ip,
                'port': port,
                'process_id': process_id,
                'log_path': service_log_dir,
                'task_id': int(task_id) if task_id else None
            }
            
            heartbeat_url = f'{video_service_api}/api/v1/algorithm_task/heartbeat/pusher'
            response = requests.post(
                heartbeat_url,
                json=heartbeat_data,
                timeout=5
            )
            
            if response.status_code == 200:
                result = response.json()
                if result.get('code') == 0:
                    logger.debug(f"✅ 心跳上报成功: pusher_id={pusher_id_int}@{server_ip}:{port}")
                else:
                    logger.warning(f"心跳上报返回错误: {result.get('msg', '未知错误')}")
            else:
                logger.warning(f"心跳上报失败: HTTP {response.status_code}")
                
        except requests.exceptions.RequestException as e:
            logger.debug(f"心跳上报请求异常: {str(e)}")
        except Exception as e:
            logger.error(f"心跳上报异常: {str(e)}", exc_info=True)
        
        time.sleep(5)

# ============================================
# 推送逻辑
# ============================================
def push_video_stream(source_url, pusher_config):
    """推送视频流
    
    Args:
        source_url: 源视频流地址
        pusher_config: Pusher配置对象
    
    Returns:
        bool: 是否成功
    """
    if not pusher_config or not pusher_config.video_stream_enabled:
        return False
    
    try:
        stream_url = pusher_config.video_stream_url
        stream_format = pusher_config.video_stream_format
        
        if not stream_url:
            logger.warning("视频流推送地址未配置")
            return False
        
        # 这里可以实现实际的视频流推送逻辑
        # 例如使用FFmpeg推流到RTMP服务器
        logger.info(f"推送视频流: {source_url} -> {stream_url} (格式: {stream_format})")
        
        # 示例：使用FFmpeg推流（实际实现需要根据需求调整）
        # ffmpeg_cmd = [
        #     'ffmpeg',
        #     '-i', source_url,
        #     '-c', 'copy',
        #     '-f', stream_format,
        #     stream_url
        # ]
        # subprocess.Popen(ffmpeg_cmd)
        
        return True
        
    except Exception as e:
        logger.error(f"推送视频流失败: {str(e)}", exc_info=True)
        return False

def push_event_alert(event_data, pusher_config):
    """推送事件告警
    
    Args:
        event_data: 事件数据字典
        pusher_config: Pusher配置对象
    
    Returns:
        bool: 是否成功
    """
    if not pusher_config or not pusher_config.event_alert_enabled:
        return False
    
    try:
        alert_url = pusher_config.event_alert_url
        alert_method = pusher_config.event_alert_method
        alert_format = pusher_config.event_alert_format
        
        if not alert_url:
            logger.warning("事件告警推送地址未配置")
            return False
        
        # 处理请求头
        headers = {}
        if pusher_config.event_alert_headers:
            try:
                headers = json.loads(pusher_config.event_alert_headers)
            except:
                logger.warning("事件告警请求头格式错误")
        
        # 处理数据模板
        payload = event_data
        if pusher_config.event_alert_template:
            try:
                template = json.loads(pusher_config.event_alert_template)
                # 这里可以实现模板变量替换
                payload = template
                # 替换模板中的变量
                for key, value in event_data.items():
                    payload_str = json.dumps(payload)
                    payload_str = payload_str.replace(f'${{{key}}}', str(value))
                    payload = json.loads(payload_str)
            except:
                logger.warning("事件告警模板格式错误，使用原始数据")
        
        # 根据推送方式发送
        if alert_method == 'http':
            # HTTP推送
            if alert_format == 'json':
                response = requests.post(
                    alert_url,
                    json=payload,
                    headers=headers,
                    timeout=10
                )
            else:
                response = requests.post(
                    alert_url,
                    data=payload,
                    headers=headers,
                    timeout=10
                )
            
            if response.status_code == 200:
                logger.info(f"事件告警推送成功: {alert_url}")
                return True
            else:
                logger.error(f"事件告警推送失败: HTTP {response.status_code}, {response.text}")
                return False
        
        elif alert_method == 'websocket':
            # WebSocket推送（需要实现WebSocket客户端）
            logger.warning("WebSocket推送暂未实现")
            return False
        
        elif alert_method == 'kafka':
            # Kafka推送（需要实现Kafka生产者）
            logger.warning("Kafka推送暂未实现")
            return False
        
        else:
            logger.error(f"不支持的事件告警推送方式: {alert_method}")
            return False
        
    except Exception as e:
        logger.error(f"推送事件告警失败: {str(e)}", exc_info=True)
        return False

# ============================================
# Flask路由
# ============================================
@app.route('/health', methods=['GET'])
def health():
    """健康检查"""
    return jsonify({
        'status': 'healthy',
        'task_id': task_id,
        'pusher_id': pusher_id,
        'running': running
    })

@app.route('/push/video', methods=['POST'])
def push_video_endpoint():
    """推送视频流接口"""
    try:
        data = request.get_json()
        if not data:
            return jsonify({
                'code': 400,
                'msg': '请求数据不能为空'
            }), 400
        
        source_url = data.get('source_url')
        if not source_url:
            return jsonify({
                'code': 400,
                'msg': 'source_url不能为空'
            }), 400
        
        # 执行推送
        success = push_video_stream(source_url, pusher)
        
        if success:
            return jsonify({
                'code': 0,
                'msg': '视频流推送成功'
            })
        else:
            return jsonify({
                'code': 500,
                'msg': '视频流推送失败'
            }), 500
        
    except Exception as e:
        logger.error(f"推送视频流接口异常: {str(e)}", exc_info=True)
        return jsonify({
            'code': 500,
            'msg': f'推送失败: {str(e)}'
        }), 500

@app.route('/push/event', methods=['POST'])
def push_event_endpoint():
    """推送事件告警接口"""
    try:
        data = request.get_json()
        if not data:
            return jsonify({
                'code': 400,
                'msg': '请求数据不能为空'
            }), 400
        
        event_data = data.get('event_data', {})
        if not isinstance(event_data, dict):
            return jsonify({
                'code': 400,
                'msg': 'event_data必须是字典'
            }), 400
        
        # 执行推送
        success = push_event_alert(event_data, pusher)
        
        if success:
            return jsonify({
                'code': 0,
                'msg': '事件告警推送成功'
            })
        else:
            return jsonify({
                'code': 500,
                'msg': '事件告警推送失败'
            }), 500
        
    except Exception as e:
        logger.error(f"推送事件告警接口异常: {str(e)}", exc_info=True)
        return jsonify({
            'code': 500,
            'msg': f'推送失败: {str(e)}'
        }), 500

@app.route('/stop', methods=['POST'])
def stop_service():
    """停止服务"""
    try:
        global running
        logger.info("收到停止服务请求")
        stop_event.set()
        running = False
        
        return jsonify({
            'code': 0,
            'msg': '服务正在停止'
        })
    except Exception as e:
        logger.error(f"停止服务失败: {str(e)}")
        return jsonify({
            'code': 500,
            'msg': f'停止服务失败: {str(e)}'
        }), 500

# ============================================
# 主函数
# ============================================
def main():
    """主函数"""
    global task, pusher, running
    
    # 解析命令行参数
    parser = argparse.ArgumentParser(description='推送器服务')
    parser.add_argument('--task-id', type=int, help='算法任务ID')
    parser.add_argument('--pusher-id', type=int, help='推送器ID')
    args = parser.parse_args()
    
    # 优先使用命令行参数，其次使用环境变量
    task_id_value = args.task_id or task_id
    pusher_id_value = args.pusher_id or pusher_id
    
    if not task_id_value:
        error_msg = "❌ TASK_ID环境变量或--task-id参数未设置"
        print(error_msg, file=sys.stderr)
        logger.error(error_msg)
        sys.exit(1)
    
    # 初始化数据库
    try:
        init_database()
    except Exception as e:
        error_msg = f"❌ 数据库初始化失败: {str(e)}"
        print(error_msg, file=sys.stderr)
        logger.error(error_msg)
        sys.exit(1)
    
    # 从数据库加载任务和配置
    try:
        task = db_session.query(AlgorithmTask).filter_by(id=task_id_value).first()
        if not task:
            error_msg = f"❌ 算法任务不存在: task_id={task_id_value}"
            print(error_msg, file=sys.stderr)
            logger.error(error_msg)
            sys.exit(1)
        
        # 获取推送器配置
        if task.pusher_id:
            pusher = db_session.query(Pusher).filter_by(id=task.pusher_id).first()
            if not pusher:
                logger.warning(f"推送器不存在: pusher_id={task.pusher_id}")
        elif pusher_id_value:
            pusher = db_session.query(Pusher).filter_by(id=pusher_id_value).first()
            if not pusher:
                logger.warning(f"推送器不存在: pusher_id={pusher_id_value}")
        
        logger.info(f"加载任务成功: task_id={task_id_value}, task_name={task.task_name}")
        logger.info(f"推送器配置: pusher_id={pusher.id if pusher else None}")
        if pusher:
            logger.info(f"视频流推送: {pusher.video_stream_enabled}")
            logger.info(f"事件告警推送: {pusher.event_alert_enabled}")
        
    except Exception as e:
        error_msg = f"❌ 加载任务配置失败: {str(e)}"
        print(error_msg, file=sys.stderr)
        logger.error(error_msg, exc_info=True)
        sys.exit(1)
    
    # 启动服务
    try:
        running = True
        stop_event.clear()
        logger.info("推送器服务已启动")
    except Exception as e:
        error_msg = f"❌ 启动服务失败: {str(e)}"
        print(error_msg, file=sys.stderr)
        logger.error(error_msg, exc_info=True)
        sys.exit(1)
    
    # 启动心跳上报线程
    try:
        heartbeat_thread = threading.Thread(target=send_heartbeat, daemon=True)
        heartbeat_thread.start()
        logger.info("心跳上报线程已启动")
    except Exception as e:
        logger.warning(f"启动心跳上报线程失败: {str(e)}")
    
    # 注册退出处理
    def signal_handler(signum, frame):
        logger.info(f"收到信号 {signum}，正在关闭服务...")
        stop_event.set()
        running = False
        sys.exit(0)
    
    signal.signal(signal.SIGTERM, signal_handler)
    signal.signal(signal.SIGINT, signal_handler)
    
    # 启动Flask服务
    try:
        port = int(os.getenv('PORT', 8003))
        host = '0.0.0.0'
        
        logger.info(f"推送器服务启动: {host}:{port}")
        logger.info("=" * 60)
        
        app.run(host=host, port=port, threaded=True, debug=False, use_reloader=False)
    except KeyboardInterrupt:
        logger.info("收到中断信号，正在关闭服务...")
        stop_event.set()
        running = False
        sys.exit(0)
    except Exception as e:
        error_msg = f"❌ 服务启动异常: {str(e)}"
        logger.error(error_msg, exc_info=True)
        stop_event.set()
        running = False
        sys.exit(1)

if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print("\n[PUSHER] 收到中断信号，正在退出...", file=sys.stderr)
        sys.exit(0)
    except Exception as e:
        import traceback
        error_msg = f"❌ [PUSHER] 主函数异常: {str(e)}"
        print(error_msg, file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)

