import { request } from '@/utils/request.js';

/**
 * 获取案例广场应用列表（已部署的应用）
 * @param {Object} data - 请求体（由调用方组装）
 * @returns {Promise<{ list: Array, totals: number, totalPages: number }>}
 */
export const fetchAppList = async (data = {}) => {
  return await request({
    url: '/portal/app/list',
    method: 'POST',
    data,
  });
};

/**
 * 获取我的应用列表
 * @param {Object} data - 请求体（由调用方组装）
 * @returns {Promise<{ list: Array, totals: number, totalPages: number }>}
 */
export const fetchMyAppList = async (data = {}) => {
  return await request({
    url: '/portal/app/my/list',
    method: 'POST',
    data,
  });
};

/**
 * 生成应用
 * @param {any} data - 请求体（通常为 FormData，由调用方组装）
 * @returns {Promise<Object>} 返回生成结果
 */
export const generateApp = async (data) => {
  // 当前后端接口接收 appId 和 requirement，兼容调用方传入的 prompt 字段。
  const body = new FormData();
  body.append('appId', data instanceof FormData ? data.get('appId') ?? '' : data?.appId ?? '');
  body.append('requirement', data instanceof FormData
    ? data.get('requirement') ?? data.get('prompt') ?? ''
    : data?.requirement ?? data?.prompt ?? '');
  return await request({
    url: '/portal/flash/apps/generate',
    method: 'POST',
    data: body,
  });
};


/**
 * 获取应用详情（包含应用信息和对话历史）
 * @param {{ appId: number|string }} params
 * @returns {Promise<Object>}
 */
export const fetchAppDetail = async (params) => {
  return await request({
    url: '/portal/app/detail',
    method: 'GET',
    params,
  });
};

// 公开案例详情始终为只读，不返回需求文档和聊天记录。
export const fetchPublishedAppDetail = (params) => request({
  url: '/portal/app/public/detail', method: 'GET', params,
});

/**
 * 获取聊天历史列表（分页）
 * @param {Object} data - 请求体（由调用方组装）
 * @returns {Promise<{ list: Array, totals: number, totalPages: number }>}
 */
export const fetchChatHistory = async (data = {}) => {
  return await request({
    url: '/portal/app/chat/history/list',
    method: 'POST',
    data,
  });
};

/**
 * 部署应用
 * @param {Object} data - 请求体（由调用方组装）
 * @returns {Promise<string>} 返回部署后的 URL
 */
export const deployApp = async (data) => {
  return await request({
    url: '/portal/flash/apps/deploy',
    method: 'POST',
    data,
  });
};

/**
 * 修改应用
 * @param {Object} data - 请求体（由调用方组装）
 * @returns {Promise<Object>}
 */
export const modifyApp = async (data) => {
  return await request({
    url: '/portal/flash/apps/edit',
    method: 'POST',
    data,
  });
};

/**
 * 创建 VSCode 编辑环境
 * @param {Object} data - 请求体（由调用方组装）
 * @returns {Promise<any>}
 */
export const createVscode = async (data) => {
  return await request({
    url: '/portal/flash/apps/vscode/edit',
    method: 'POST',
    data,
  });
};

export const fetchSourceFiles = (data) => request({ url: '/portal/flash/apps/source/list', method: 'POST', data });
export const saveSourceFile = (data) => request({ url: '/portal/flash/apps/source/save', method: 'POST', data });

/**
 * 完成编辑
 * @param {Object} data - 请求体（由调用方组装）
 * @returns {Promise<any>}
 */
export const completeEdit = async (data) => {
  return await request({
    url: '/portal/flash/app/complete-edit',
    method: 'POST',
    data,
  });
};

