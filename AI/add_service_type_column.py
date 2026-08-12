"""
数据库迁移脚本：为 llm_config 表添加 service_type 列
@author reese
@email reese
"""
import os
import sys
from dotenv import load_dotenv
from sqlalchemy import create_engine, text, inspect
from sqlalchemy.exc import ProgrammingError

# 加载环境变量
def load_env_file(env_name=''):
    if env_name:
        env_file = f'.env.{env_name}'
        if os.path.exists(env_file):
            load_dotenv(env_file, override=True)
            print(f"✅ 已加载配置文件: {env_file}")
        else:
            print(f"⚠️  配置文件 {env_file} 不存在，尝试加载默认 .env 文件")
            if os.path.exists('.env'):
                load_dotenv('.env', override=True)
                print(f"✅ 已加载默认配置文件: .env")
    else:
        if os.path.exists('.env'):
            load_dotenv('.env', override=True)
            print(f"✅ 已加载默认配置文件: .env")

# 解析命令行参数
env_name = ''
if len(sys.argv) > 1:
    env_name = sys.argv[1]

load_env_file(env_name)

# 获取数据库URL
database_url = os.environ.get('DATABASE_URL')
if not database_url:
    print("❌ 错误: DATABASE_URL环境变量未设置")
    sys.exit(1)

# 转换postgres://为postgresql://（SQLAlchemy要求）
database_url = database_url.replace("postgres://", "postgresql://", 1)

try:
    # 创建数据库引擎
    engine = create_engine(database_url)
    
    # 检查列是否已存在
    inspector = inspect(engine)
    columns = [col['name'] for col in inspector.get_columns('llm_config')]
    
    if 'service_type' in columns:
        print("✅ service_type 列已存在，无需添加")
    else:
        print("📝 开始添加 service_type 列...")
        
        # 添加 service_type 列
        with engine.connect() as conn:
            # 开始事务
            trans = conn.begin()
            try:
                # 添加列（带默认值）
                conn.execute(text("""
                    ALTER TABLE llm_config 
                    ADD COLUMN service_type VARCHAR(20) NOT NULL DEFAULT 'online'
                """))
                
                # 为现有记录设置默认值（虽然已经有DEFAULT，但为了确保）
                conn.execute(text("""
                    UPDATE llm_config 
                    SET service_type = 'online' 
                    WHERE service_type IS NULL
                """))
                
                # 提交事务
                trans.commit()
                print("✅ service_type 列添加成功")
            except Exception as e:
                trans.rollback()
                print(f"❌ 添加列失败: {str(e)}")
                raise
    
    # 验证列是否存在
    inspector = inspect(engine)
    columns = [col['name'] for col in inspector.get_columns('llm_config')]
    if 'service_type' in columns:
        print("✅ 验证成功: service_type 列已存在于 llm_config 表中")
    else:
        print("❌ 验证失败: service_type 列未找到")
        sys.exit(1)
        
except Exception as e:
    print(f"❌ 执行失败: {str(e)}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

print("🎉 迁移完成！")
