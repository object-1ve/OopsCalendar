# -*- coding: utf-8 -*-
"""OopsCalendar 后端入口(FastAPI + uvicorn,端口默认 8080)。"""
import logging

import uvicorn

import config

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    uvicorn.run(
        "app.web:create_app_instance",
        factory=True,
        host=config.HOST,
        port=config.PORT,
        reload=False,
    )
