<template>
  <div class="about-page">
    <div class="about-page__bg">
      <div class="bg-gradient"></div>
      <div class="bg-grid"></div>
      <div class="bg-noise"></div>
    </div>

    <div class="about-container">
      <div class="about-header">
        <div class="about-header__icon">
          <div class="icon-glow"></div>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M12 2L2 7l10 5 10-5-10-5z" />
            <path d="M2 17l10 5 10-5" />
            <path d="M2 12l10 5 10-5" />
          </svg>
        </div>
        <h1 class="about-header__title">Super Friend</h1>
        <p class="about-header__subtitle">智能AI助手平台 — 让AI成为您的工作伙伴</p>
      </div>

      <div class="about-content">
        <div class="content-section">
          <div class="section-header">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="16" x2="12" y2="12" />
              <line x1="12" y1="8" x2="12.01" y2="8" />
            </svg>
            <span>认识 Super Friend</span>
          </div>
          <div class="intro-card">
            <p class="intro-text">
              Super Friend 是一个<strong>智能AI助手平台</strong>，它不仅仅是一个聊天机器人，而是一个能够<strong>真正帮助您完成工作的AI伙伴</strong>。通过集成先进的AI模型和丰富的工具系统，Super Friend 可以帮您处理各种复杂任务，从简单的问答到复杂的多步骤项目规划。
            </p>
            <p class="intro-text">
              想象一下，您有一个永不疲倦的助手，可以帮您<strong>分析数据、编写代码、搜索信息、管理文件、创建文档</strong>，甚至能够<strong>记住您的重要偏好和习惯</strong>，在后续对话中为您提供更个性化的服务。这就是 Super Friend。
            </p>
          </div>
        </div>

        <div class="content-section">
          <div class="section-header">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
            </svg>
            <span>核心功能</span>
          </div>
          <div class="feature-grid">
            <div class="feature-card">
              <div class="feature-card__icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                </svg>
              </div>
              <h3>智能多轮对话</h3>
              <p>支持连续对话和上下文理解，AI能够记住之前的讨论内容，实现真正的有意义的交流。无论是长篇讨论还是简短问答，都能保持连贯性。</p>
            </div>
            <div class="feature-card">
              <div class="feature-card__icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />
                </svg>
              </div>
              <h3>强大的工具调用</h3>
              <p>通过MCP协议连接多种工具，可以执行文件操作、网页搜索、代码执行、数据分析等实际任务。不只是回答问题，而是真正完成任务。</p>
            </div>
            <div class="feature-card">
              <div class="feature-card__icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M12 2L2 7l10 5 10-5-10-5z" />
                  <path d="M2 17l10 5 10-5" />
                  <path d="M2 12l10 5 10-5" />
                </svg>
              </div>
              <h3>模块化技能系统</h3>
              <p>内置多种专业技能（如文档处理、PPT制作、数据分析等），AI会根据任务自动选择合适的技能，也可以手动管理技能开关。</p>
            </div>
            <div class="feature-card">
              <div class="feature-card__icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10" />
                  <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
                  <line x1="12" y1="17" x2="12.01" y2="17" />
                </svg>
              </div>
              <h3>知识图谱</h3>
              <p>自动从对话中提取和组织知识，构建个人知识网络。可以手动添加、编辑知识节点，让AI更好地理解和记住您的需求。</p>
            </div>
            <div class="feature-card">
              <div class="feature-card__icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M12 2v4" />
                  <path d="M12 18v4" />
                  <path d="M4.93 4.93l2.83 2.83" />
                  <path d="M16.24 16.24l2.83 2.83" />
                  <path d="M2 12h4" />
                  <path d="M18 12h4" />
                  <path d="M4.93 19.07l2.83-2.83" />
                  <path d="M16.24 7.76l2.83-2.83" />
                </svg>
              </div>
              <h3>智能上下文压缩</h3>
              <p>当对话历史过长时，系统会自动进行智能压缩，保留最重要的信息，确保AI始终能够理解对话的核心内容。</p>
            </div>
            <div class="feature-card">
              <div class="feature-card__icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                  <polyline points="17 8 12 3 7 8" />
                  <line x1="12" y1="3" x2="12" y2="15" />
                </svg>
              </div>
              <h3>多模态文件支持</h3>
              <p>支持上传图片、文档等多种文件类型，可以分析图片内容、处理Excel表格、解读PDF文档，让AI能够处理丰富多样的信息。</p>
            </div>
            <div class="feature-card">
              <div class="feature-card__icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="20" x2="18" y2="10" />
                  <line x1="12" y1="20" x2="12" y2="4" />
                  <line x1="6" y1="20" x2="6" y2="14" />
                </svg>
              </div>
              <h3>使用统计与追踪</h3>
              <p>实时显示Token消耗、对话次数、模型使用情况等数据，帮助您了解AI资源的使用情况，优化使用效率。</p>
            </div>
            <div class="feature-card">
              <div class="feature-card__icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                  <circle cx="9" cy="7" r="4" />
                  <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
                  <path d="M16 3.13a4 4 0 0 1 0 7.75" />
                </svg>
              </div>
              <h3>会话与文件夹管理</h3>
              <p>支持创建多个对话会话，并可以将相关会话整理到文件夹中，方便管理和回顾重要的讨论内容。</p>
            </div>
          </div>
        </div>

        <div class="content-section">
          <div class="section-header">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
              <line x1="3" y1="9" x2="21" y2="9" />
              <line x1="9" y1="21" x2="9" y2="9" />
            </svg>
            <span>三种任务模式</span>
          </div>
          <div class="mode-cards">
            <div class="mode-card mode-card--ai">
              <div class="mode-card__header">
                <div class="mode-card__icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
                  </svg>
                </div>
                <h3>简单任务模式</h3>
                <span class="mode-badge">快速响应</span>
              </div>
              <p class="mode-card__desc">适合快速问答和简单操作，AI直接给出答案或执行简单任务。响应速度快，适合日常查询和信息获取。</p>
              <div class="mode-card__list">
                <h4>适用场景：</h4>
                <ul>
                  <li>翻译和解释文本</li>
                  <li>快速计算和转换</li>
                  <li>简单的问题解答</li>
                  <li>一键生成简短内容</li>
                </ul>
              </div>
            </div>
            <div class="mode-card mode-card--medium">
              <div class="mode-card__header">
                <div class="mode-card__icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10" />
                    <polyline points="12 6 12 12 16 14" />
                  </svg>
                </div>
                <h3>中等任务模式</h3>
                <span class="mode-badge">智能规划</span>
              </div>
              <p class="mode-card__desc">适合需要多步骤处理的复杂任务，AI会进行推理分析，可以调用工具完成任务，并具有自我纠错能力。</p>
              <div class="mode-card__list">
                <h4>适用场景：</h4>
                <ul>
                  <li>代码编写和调试</li>
                  <li>文档分析和总结</li>
                  <li>数据处理和分析</li>
                  <li>多步骤的工作流程</li>
                </ul>
              </div>
            </div>
            <div class="mode-card mode-card--complex">
              <div class="mode-card__header">
                <div class="mode-card__icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
                    <polyline points="22 4 12 14.01 9 11.01" />
                  </svg>
                </div>
                <h3>复杂任务模式</h3>
                <span class="mode-badge">深度规划</span>
              </div>
              <p class="mode-card__desc">适合大型项目和多阶段任务，AI会制定详细计划，分步执行，管理依赖关系，处理复杂的工作流程。</p>
              <div class="mode-card__list">
                <h4>适用场景：</h4>
                <ul>
                  <li>大型项目规划和分解</li>
                  <li>系统设计和架构</li>
                  <li>复杂的数据分析项目</li>
                  <li>需要多工具协作的任务</li>
                </ul>
              </div>
            </div>
          </div>
        </div>

        <div class="content-section">
          <div class="section-header">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />
            </svg>
            <span>MCP 工具生态系统</span>
          </div>
          <div class="tools-intro">
            <p class="intro-text">MCP（Model Context Protocol）是连接AI与现实世界的桥梁。通过MCP协议，Super Friend可以调用各种专业工具，真正完成实际工作。</p>
          </div>
          <div class="tools-grid">
            <div class="tool-category">
              <h4><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><polyline points="14 2 14 8 20 8" /></svg> 文件系统工具</h4>
              <ul>
                <li>读取文件和代码</li>
                <li>创建和编辑文件</li>
                <li>批量文件操作</li>
                <li>文件夹管理</li>
              </ul>
            </div>
            <div class="tool-category">
              <h4><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10" /><line x1="2" y1="12" x2="22" y2="12" /><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" /></svg> 网页浏览工具</h4>
              <ul>
                <li>搜索网络信息</li>
                <li>获取网页内容</li>
                <li>提取关键数据</li>
                <li>价格和新闻查询</li>
              </ul>
            </div>
            <div class="tool-category">
              <h4><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="16 18 22 12 16 6" /><polyline points="8 6 2 12 8 18" /></svg> 代码执行工具</h4>
              <ul>
                <li>运行Python代码</li>
                <li>执行Shell命令</li>
                <li>代码分析和解释</li>
                <li>自动化脚本执行</li>
              </ul>
            </div>
            <div class="tool-category">
              <h4><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><polyline points="14 2 14 8 20 8" /><line x1="8" y1="13" x2="16" y2="13" /><line x1="8" y1="17" x2="16" y2="17" /></svg> 办公文档工具</h4>
              <ul>
                <li>Excel数据分析</li>
                <li>Word文档处理</li>
                <li>PPT演示制作</li>
                <li>PDF内容提取</li>
              </ul>
            </div>
          </div>
        </div>

        <div class="content-section">
          <div class="section-header">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
            </svg>
            <span>快速入门指南</span>
          </div>
          <div class="quick-start">
            <div class="step-card">
              <div class="step-card__number">1</div>
              <h3>开始对话</h3>
              <p>在底部输入框中输入您的问题或任务描述，按Enter键发送。AI会立即开始响应您的请求。</p>
            </div>
            <div class="step-card">
              <div class="step-card__number">2</div>
              <h3>选择合适的模式</h3>
              <p>根据任务复杂度选择模式：简单任务用快速模式，复杂工作用深度规划模式。</p>
            </div>
            <div class="step-card">
              <div class="step-card__number">3</div>
              <h3>上传所需文件</h3>
              <p>如果有需要处理的文件（图片、Excel、文档等），点击上传按钮添加附件。</p>
            </div>
            <div class="step-card">
              <div class="step-card__number">4</div>
              <h3>查看执行结果</h3>
              <p>观察AI的执行过程，查看生成的文件或结果。根据需要继续对话或开启新话题。</p>
            </div>
          </div>
        </div>

        <div class="content-section">
          <div class="section-header">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
              <line x1="9" y1="10" x2="15" y2="10" />
              <line x1="12" y1="7" x2="12" y2="13" />
            </svg>
            <span>使用示例</span>
          </div>
          <div class="example-dialogs">
            <div class="example-card">
              <div class="example-card__header">
                <span class="example-badge example-badge--data">数据分析</span>
                <h3>Excel数据处理</h3>
              </div>
              <div class="example-card__content">
                <div class="example-message example-message--user">
                  <span class="message-label">用户：</span>
                  <span class="message-text">帮我分析这个销售数据Excel文件，找出销售额最高的产品类别，并生成图表</span>
                </div>
                <div class="example-message example-message--assistant">
                  <span class="message-label">AI：</span>
                  <span class="message-text">好的，我先读取您的Excel文件...分析完成！根据数据，电子产品类别是销售额最高的，总计¥1,234,567。我来为您生成一个销售趋势图表...</span>
                </div>
              </div>
            </div>
            <div class="example-card">
              <div class="example-card__header">
                <span class="example-badge example-badge--code">代码开发</span>
                <h3>编写和调试代码</h3>
              </div>
              <div class="example-card__content">
                <div class="example-message example-message--user">
                  <span class="message-label">用户：</span>
                  <span class="message-text">用Python写一个自动备份文件夹的脚本，支持增量备份和日志记录</span>
                </div>
                <div class="example-message example-message--assistant">
                  <span class="message-label">AI：</span>
                  <span class="message-text">我来为您创建一个功能完善的备份脚本...脚本已创建完成，包含以下功能：1）增量备份，只备份修改过的文件；2）完整的日志记录；3）错误处理和邮件通知。</span>
                </div>
              </div>
            </div>
            <div class="example-card">
              <div class="example-card__header">
                <span class="example-badge example-badge--doc">文档处理</span>
                <h3>一键生成PPT</h3>
              </div>
              <div class="example-card__content">
                <div class="example-message example-message--user">
                  <span class="message-label">用户：</span>
                  <span class="message-text">帮我把这份产品需求文档转换成PPT演示文稿，包含封面、目录、各章节内容和总结页</span>
                </div>
                <div class="example-message example-message--assistant">
                  <span class="message-label">AI：</span>
                  <span class="message-text">好的，我来读取文档内容并制作PPT...PPT已生成，包含12页幻灯片：封面、产品概述、功能需求、用户故事、技术方案、时间规划、风险评估、团队介绍、Q&A页面等。</span>
                </div>
              </div>
            </div>
            <div class="example-card">
              <div class="example-card__header">
                <span class="example-badge example-badge--search">信息检索</span>
                <h3>网络信息搜索</h3>
              </div>
              <div class="example-card__content">
                <div class="example-message example-message--user">
                  <span class="message-label">用户：</span>
                  <span class="message-text">帮我搜索一下最近一周关于人工智能在医疗领域应用的最新新闻</span>
                </div>
                <div class="example-message example-message--assistant">
                  <span class="message-label">AI：</span>
                  <span class="message-text">好的，我来搜索最新的行业新闻...找到了5条重要新闻：1）某公司AI诊断系统获FDA批准；2）斯坦福大学发布最新医疗AI报告；3）国内首个AI辅助手术中心成立...</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="content-section">
          <div class="section-header">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10" />
              <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
              <line x1="12" y1="17" x2="12.01" y2="17" />
            </svg>
            <span>常见问题解答</span>
          </div>
          <div class="faq-list">
            <el-collapse v-model="activeFaq">
              <el-collapse-item title="Super Friend和其他AI聊天机器人有什么不同？" name="1">
                <p>Super Friend 不只是一个回答问题的AI，它是一个<strong>能够执行实际任务的助手</strong>。它可以读写文件、搜索网页、执行代码、处理数据，真正帮您完成工作。同时它有强大的记忆和知识管理功能，能够持续学习和适应您的需求。</p>
              </el-collapse-item>
              <el-collapse-item title="我应该什么时候使用简单模式，什么时候使用复杂模式？" name="2">
                <p><strong>简单模式</strong>适合快速问答、简单计算、翻译等耗时较短的任务。<strong>复杂模式</strong>适合需要多步骤处理、多个工具协作、详细规划的复杂工作。如果您不确定，先从简单模式开始，效果不好再切换到复杂模式。</p>
              </el-collapse-item>
              <el-collapse-item title="我的对话数据会被保存吗？" name="3">
                <p>对话历史会保存在本地设备上，方便您回顾之前的讨论。系统会自动对过长的对话进行智能压缩，保留核心信息。您可以随时在侧边栏清除历史记录。</p>
              </el-collapse-item>
              <el-collapse-item title="如何让AI更好地记住我的偏好？" name="4">
                <p>您可以通过以下方式帮助AI记住您的偏好：1）在对话中明确说明您的习惯和偏好；2）使用知识图谱功能手动添加重要信息；3）在新对话中简单提及之前的背景信息。AI会结合这些信息提供更个性化的服务。</p>
              </el-collapse-item>
              <el-collapse-item title="可以同时处理多个任务吗？" name="5">
                <p>在复杂任务模式下，AI可以将大型任务分解为多个子任务，按顺序或并行执行。每个子任务的进度都可以追踪，您可以随时暂停或调整计划。</p>
              </el-collapse-item>
              <el-collapse-item title="如何管理不同的对话？" name="6">
                <p>在侧边栏中，您可以创建多个会话来分别处理不同的话题。还可以创建文件夹，将相关的会话整理在一起，方便日后查找和回顾。</p>
              </el-collapse-item>
            </el-collapse>
          </div>
        </div>

        <div class="about-footer">
          <p>© 2025 Super Friend. 让AI成为您的工作伙伴</p>
          <p>版本 1.0.0 | 智能 · 高效 · 可靠</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const activeFaq = ref(['1', '2', '3'])
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.about-page {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--chat-page-bg);
}

.about-page__bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 0;
  overflow: hidden;

  .bg-gradient {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: var(--chat-page-gradient);
  }

  .bg-grid {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-image: 
      linear-gradient(var(--chat-border-subtle) 1px, transparent 1px),
      linear-gradient(90deg, var(--chat-border-subtle) 1px, transparent 1px);
    background-size: 60px 60px;
    mask-image: radial-gradient(ellipse 80% 60% at 50% 50%, black, transparent);
  }

  .bg-noise {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    opacity: 0.15;
    background-image: var(--chat-page-noise);
    pointer-events: none;
  }
}

.about-container {
  flex: 1;
  overflow-y: auto;
  padding: 40px 20px;
  position: relative;
  z-index: 1;
}

.about-header {
  text-align: center;
  margin-bottom: 40px;
  padding: 48px 40px;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: var(--chat-surface-glass);
    border: 1px solid var(--chat-border-subtle);
    border-radius: 24px;
    backdrop-filter: blur(20px);
  }
}

.about-header__icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 20px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;

  .icon-glow {
    position: absolute;
    top: 50%;
    left: 50%;
    width: 120px;
    height: 120px;
    transform: translate(-50%, -50%);
    background: radial-gradient(circle, rgba(139, 92, 246, 0.4) 0%, transparent 70%);
    animation: pulse 4s ease-in-out infinite;
  }

  svg {
    width: 48px;
    height: 48px;
    color: var(--chat-text-primary);
    position: relative;
    z-index: 1;
  }
}

.about-header__title {
  font-size: 32px;
  font-weight: 700;
  color: var(--chat-text-primary);
  margin-bottom: 8px;
  letter-spacing: -0.5px;
  position: relative;
  z-index: 1;
}

.about-header__subtitle {
  font-size: 16px;
  color: var(--chat-text-tertiary);
  margin: 0;
  position: relative;
  z-index: 1;
}

.about-content {
  max-width: 1200px;
  margin: 0 auto;
}

.content-section {
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 16px;
  padding: 32px;
  margin-bottom: 24px;
  backdrop-filter: blur(20px);
  transition: all 0.3s ease;

  &:hover {
    border-color: var(--chat-border-default);
    background: var(--chat-surface-glass-hover);
  }
}

.section-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
  color: var(--chat-text-primary);
  font-size: 20px;
  font-weight: 600;

  svg {
    width: 24px;
    height: 24px;
    color: var(--chat-accent-purple);
  }
}

.intro-card {
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 12px;
  padding: 24px;
}

.intro-text {
  font-size: 15px;
  color: var(--chat-text-secondary);
  line-height: 1.8;
  margin: 0 0 16px;

  &:last-child {
    margin-bottom: 0;
  }

  strong {
    color: var(--chat-accent-purple);
    font-weight: 600;
  }
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 20px;
}

.feature-card {
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 12px;
  padding: 24px;
  text-align: center;
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-4px);
    border-color: rgba(139, 92, 246, 0.3);
    background: rgba(139, 92, 246, 0.05);
  }

  &__icon {
    width: 56px;
    height: 56px;
    margin: 0 auto 16px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, rgba(139, 92, 246, 0.15) 0%, rgba(139, 92, 246, 0.05) 100%);
    border-radius: 14px;

    svg {
      width: 28px;
      height: 28px;
      color: var(--chat-accent-purple);
    }
  }

  h3 {
    font-size: 17px;
    font-weight: 600;
    color: var(--chat-text-primary);
    margin-bottom: 12px;
  }

  p {
    font-size: 14px;
    color: var(--chat-text-tertiary);
    line-height: 1.6;
    margin: 0;
  }
}

.mode-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 20px;
}

.mode-card {
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 12px;
  padding: 24px;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    width: 4px;
    height: 100%;
    background: var(--chat-gradient-primary);
  }

  &--ai::before {
    background: linear-gradient(180deg, var(--chat-accent-purple) 0%, var(--chat-accent-cyan) 100%);
  }

  &--medium::before {
    background: linear-gradient(180deg, var(--chat-accent-cyan) 0%, var(--chat-accent-green) 100%);
  }

  &--complex::before {
    background: linear-gradient(180deg, var(--chat-accent-pink) 0%, var(--chat-accent-purple) 100%);
  }

  &:hover {
    border-color: var(--chat-border-default);
    background: var(--chat-surface-glass-hover);
  }

  &__header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
    flex-wrap: wrap;
  }

  &__icon {
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--chat-surface-glass);
    border-radius: 10px;

    svg {
      width: 20px;
      height: 20px;
      color: var(--chat-text-secondary);
    }
  }

  h3 {
    font-size: 18px;
    font-weight: 600;
    color: var(--chat-text-primary);
    margin: 0;
  }

  .mode-badge {
    padding: 4px 10px;
    border-radius: 20px;
    font-size: 11px;
    font-weight: 600;
    color: white;
    background: var(--chat-gradient-primary);
    margin-left: auto;
  }

  &__desc {
    font-size: 14px;
    color: var(--chat-text-tertiary);
    line-height: 1.6;
    margin-bottom: 20px;
  }

  &__list {
    h4 {
      font-size: 13px;
      font-weight: 600;
      color: var(--chat-text-secondary);
      margin-bottom: 12px;
    }

    ul {
      margin: 0;
      padding-left: 20px;
      color: var(--chat-text-tertiary);
      font-size: 13px;
      line-height: 1.8;

      li {
        margin-bottom: 6px;
      }
    }
  }
}

.tools-intro {
  margin-bottom: 24px;
}

.tools-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
}

.tool-category {
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 12px;
  padding: 20px;
  transition: all 0.3s ease;

  &:hover {
    border-color: rgba(139, 92, 246, 0.3);
    background: rgba(139, 92, 246, 0.03);
  }

  h4 {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 15px;
    font-weight: 600;
    color: var(--chat-text-primary);
    margin-bottom: 16px;

    svg {
      width: 20px;
      height: 20px;
      color: var(--chat-accent-purple);
    }
  }

  ul {
    margin: 0;
    padding-left: 18px;
    color: var(--chat-text-tertiary);
    font-size: 13px;
    line-height: 1.8;

    li {
      margin-bottom: 6px;
    }
  }
}

.quick-start {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 20px;
}

.step-card {
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 12px;
  padding: 24px;
  text-align: center;
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-4px);
    border-color: rgba(139, 92, 246, 0.3);
    background: rgba(139, 92, 246, 0.05);
  }

  &__number {
    width: 48px;
    height: 48px;
    margin: 0 auto 16px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--chat-gradient-glow);
    border-radius: 50%;
    font-size: 20px;
    font-weight: 700;
    color: var(--chat-text-primary);
  }

  h3 {
    font-size: 16px;
    font-weight: 600;
    color: var(--chat-text-primary);
    margin-bottom: 12px;
  }

  p {
    font-size: 13px;
    color: var(--chat-text-tertiary);
    line-height: 1.6;
    margin: 0;
  }
}

.example-dialogs {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.example-card {
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 12px;
  padding: 20px;
  transition: all 0.3s ease;

  &:hover {
    border-color: var(--chat-border-default);
    background: var(--chat-surface-glass-hover);
  }

  &__header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
  }

  h3 {
    font-size: 15px;
    font-weight: 600;
    color: var(--chat-text-primary);
    margin: 0;
  }

  &__content {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
}

.example-badge {
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  color: white;

  &--ai {
    background: linear-gradient(135deg, var(--chat-accent-purple) 0%, var(--chat-accent-cyan) 100%);
  }

  &--data {
    background: linear-gradient(135deg, var(--chat-accent-green) 0%, var(--chat-accent-cyan) 100%);
  }

  &--code {
    background: linear-gradient(135deg, var(--chat-accent-cyan) 0%, var(--chat-accent-purple) 100%);
  }

  &--doc {
    background: linear-gradient(135deg, var(--chat-accent-pink) 0%, var(--chat-accent-purple) 100%);
  }

  &--search {
    background: linear-gradient(135deg, var(--chat-accent-orange) 0%, var(--chat-accent-pink) 100%);
  }
}

.example-message {
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.6;

  &--user {
    background: rgba(139, 92, 246, 0.1);
    border-left: 3px solid var(--chat-accent-purple);
  }

  &--assistant {
    background: rgba(16, 185, 129, 0.1);
    border-left: 3px solid var(--chat-accent-green);
  }

  .message-label {
    font-weight: 600;
    color: var(--chat-text-secondary);
    margin-right: 8px;
  }

  .message-text {
    color: var(--chat-text-primary);
  }
}

.faq-list {
  :deep(.el-collapse) {
    border: none;
    background: transparent;
  }

  :deep(.el-collapse-item__header) {
    font-size: 14px;
    font-weight: 600;
    color: var(--chat-text-primary);
    padding: 16px 0;
    border-bottom: 1px solid var(--chat-border-subtle);
    background: transparent;

    &:hover {
      color: var(--chat-accent-purple);
    }
  }

  :deep(.el-collapse-item__wrap) {
    background: transparent;
    border-bottom: none;
  }

  :deep(.el-collapse-item__content) {
    padding: 16px 0;
    font-size: 14px;
    color: var(--chat-text-tertiary);
    line-height: 1.7;

    strong {
      color: var(--chat-accent-purple);
      font-weight: 600;
    }
  }

  :deep(.el-collapse-item__arrow) {
    color: var(--chat-text-tertiary);
  }
}

.about-footer {
  text-align: center;
  padding: 40px 20px;
  color: var(--chat-text-muted);
  font-size: 13px;

  p {
    margin: 4px 0;
  }
}

@keyframes pulse {
  0%, 100% {
    opacity: 0.4;
    transform: translate(-50%, -50%) scale(1);
  }
  50% {
    opacity: 0.6;
    transform: translate(-50%, -50%) scale(1.1);
  }
}

@media (max-width: 768px) {
  .about-container {
    padding: 20px 12px;
  }

  .about-header {
    padding: 32px 20px;
  }

  .about-header__title {
    font-size: 24px;
  }

  .content-section {
    padding: 20px;
  }

  .feature-grid {
    grid-template-columns: 1fr;
  }

  .mode-cards {
    grid-template-columns: 1fr;
  }

  .quick-start {
    grid-template-columns: 1fr;
  }

  .tools-grid {
    grid-template-columns: 1fr;
  }
}
</style>
