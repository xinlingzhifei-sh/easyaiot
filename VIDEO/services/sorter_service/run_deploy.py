"""
排序器服务模板工程
用于对检测结果进行排序

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
from models import db, AlgorithmTask, Sorter

# ============================================
# 全局异常处理器
# ============================================
def handle_exception(exc_type, exc_value, exc_traceback):
    """全局异常处理器"""
    if issubclass(exc_type, KeyboardInterrupt):
        sys.__excepthook__(exc_type, exc_value, exc_traceback)
        return
    
    import traceback
    error_msg = f"❌ [SORTER] 未捕获的异常: {exc_type.__name__}: {exc_value}"
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
sorter_id = os.getenv('SORTER_ID')  # 排序器ID

# 日志目录
log_path = os.getenv('LOG_PATH')
if log_path:
    service_log_dir = log_path
else:
    video_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    logs_base_dir = os.path.join(video_root, 'logs')
    service_log_dir = os.path.join(logs_base_dir, f'sorter_{service_id}')
os.makedirs(service_log_dir, exist_ok=True)

# 创建日志格式
log_format = '[SORTER] %(asctime)s - %(name)s - %(levelname)s - %(message)s'
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
logger.info("🚀 排序器服务启动")
logger.info(f"任务ID: {task_id}")
logger.info(f"排序器ID: {sorter_id}")
logger.info(f"日志目录: {service_log_dir}")
logger.info("=" * 60)

# 全局变量
db_session = None
task = None
sorter = None
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
    global video_service_api, server_ip, port, process_id, sorter_id, task_id, service_log_dir
    
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
                port = int(os.getenv('PORT', 8002))
            
            if not sorter_id:
                logger.warning("SORTER_ID 环境变量未设置，无法发送心跳")
                time.sleep(60)
                continue
            
            try:
                sorter_id_int = int(sorter_id)
            except (ValueError, TypeError):
                logger.error(f"SORTER_ID 无效: {sorter_id}，必须是数字")
                time.sleep(60)
                continue
            
            heartbeat_data = {
                'sorter_id': sorter_id_int,
                'server_ip': server_ip,
                'port': port,
                'process_id': process_id,
                'log_path': service_log_dir,
                'task_id': int(task_id) if task_id else None
            }
            
            heartbeat_url = f'{video_service_api}/api/v1/algorithm_task/heartbeat/sorter'
            response = requests.post(
                heartbeat_url,
                json=heartbeat_data,
                timeout=5
            )
            
            if response.status_code == 200:
                result = response.json()
                if result.get('code') == 0:
                    logger.debug(f"✅ 心跳上报成功: sorter_id={sorter_id_int}@{server_ip}:{port}")
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
# 排序逻辑
# ============================================
def sort_detections(detections, sorter_config):
    """对检测结果进行排序
    
    Args:
        detections: 检测结果列表，每个元素包含检测信息
        sorter_config: Sorter配置对象
    
    Returns:
        list: 排序后的检测结果列表
    """
    if not detections:
        return []
    
    if not sorter_config:
        return detections
    
    sorter_type = sorter_config.sorter_type
    sort_order = sorter_config.sort_order
    
    try:
        if sorter_type == 'confidence':
            # 按置信度排序
            sorted_detections = sorted(
                detections,
                key=lambda x: x.get('confidence', 0.0),
                reverse=(sort_order == 'desc')
            )
        elif sorter_type == 'time':
            # 按时间排序
            sorted_detections = sorted(
                detections,
                key=lambda x: x.get('timestamp', 0),
                reverse=(sort_order == 'desc')
            )
        elif sorter_type == 'score':
            # 按分数排序
            sorted_detections = sorted(
                detections,
                key=lambda x: x.get('score', 0.0),
                reverse=(sort_order == 'desc')
            )
        else:
            # 默认不排序
            sorted_detections = detections
        
        logger.debug(f"排序完成: 类型={sorter_type}, 顺序={sort_order}, 数量={len(sorted_detections)}")
        return sorted_detections
        
    except Exception as e:
        logger.error(f"排序失败: {str(e)}", exc_info=True)
        return detections

# ============================================
# Flask路由
# ============================================
@app.route('/health', methods=['GET'])
def health():
    """健康检查"""
    return jsonify({
        'status': 'healthy',
        'task_id': task_id,
        'sorter_id': sorter_id,
        'running': running
    })

@app.route('/sort', methods=['POST'])
def sort_endpoint():
    """排序接口"""
    try:
        data = request.get_json()
        if not data:
            return jsonify({
                'code': 400,
                'msg': '请求数据不能为空'
            }), 400
        
        detections = data.get('detections', [])
        if not isinstance(detections, list):
            return jsonify({
                'code': 400,
                'msg': 'detections必须是列表'
            }), 400
        
        # 执行排序
        sorted_detections = sort_detections(detections, sorter)
        
        return jsonify({
            'code': 0,
            'msg': '排序成功',
            'data': {
                'detections': sorted_detections,
                'count': len(sorted_detections)
            }
        })
        
    except Exception as e:
        logger.error(f"排序接口异常: {str(e)}", exc_info=True)
        return jsonify({
            'code': 500,
            'msg': f'排序失败: {str(e)}'
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
    global task, sorter, running
    
    # 解析命令行参数
    parser = argparse.ArgumentParser(description='排序器服务')
    parser.add_argument('--task-id', type=int, help='算法任务ID')
    parser.add_argument('--sorter-id', type=int, help='排序器ID')
    args = parser.parse_args()
    
    # 优先使用命令行参数，其次使用环境变量
    task_id_value = args.task_id or task_id
    sorter_id_value = args.sorter_id or sorter_id
    
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
        
        # 获取排序器配置
        if task.sorter_id:
            sorter = db_session.query(Sorter).filter_by(id=task.sorter_id).first()
            if not sorter:
                logger.warning(f"排序器不存在: sorter_id={task.sorter_id}")
        elif sorter_id_value:
            sorter = db_session.query(Sorter).filter_by(id=sorter_id_value).first()
            if not sorter:
                logger.warning(f"排序器不存在: sorter_id={sorter_id_value}")
        
        logger.info(f"加载任务成功: task_id={task_id_value}, task_name={task.task_name}")
        logger.info(f"排序器配置: sorter_id={sorter.id if sorter else None}")
        
    except Exception as e:
        error_msg = f"❌ 加载任务配置失败: {str(e)}"
        print(error_msg, file=sys.stderr)
        logger.error(error_msg, exc_info=True)
        sys.exit(1)
    
    # 启动服务
    try:
        running = True
        stop_event.clear()
        logger.info("排序器服务已启动")
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
        port = int(os.getenv('PORT', 8002))
        host = '0.0.0.0'
        
        logger.info(f"排序器服务启动: {host}:{port}")
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
        print("\n[SORTER] 收到中断信号，正在退出...", file=sys.stderr)
        sys.exit(0)
    except Exception as e:
        import traceback
        error_msg = f"❌ [SORTER] 主函数异常: {str(e)}"
        print(error_msg, file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)

