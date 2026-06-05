<template>
  <div class="skill-manager">
    <div class="skill-manager__bg">
      <div class="bg-gradient"></div>
      <div class="bg-grid"></div>
      <div class="bg-noise"></div>
    </div>

    <div class="skill-manager__main">
      <div class="skill-manager__header">
        <div class="header-content">
          <h1>技能管理</h1>
          <p>管理和配置 Agent 技能，提升对话体验</p>
        </div>
        <div class="header-actions">
          <button class="btn btn--success" v-if="hasPendingChanges" @click="saveAllChanges" :disabled="isSaving">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="20 6 9 17 4 12" />
            </svg>
            {{ isSaving ? '保存中...' : `保存修改 (${pendingChanges.size})` }}
          </button>
          <button class="btn btn--ghost" v-if="hasPendingChanges" @click="discardChanges">
            放弃修改
          </button>
          <button class="btn btn--primary" @click="showCreateDialog = true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
            创建技能
          </button>
          <button class="btn btn--accent" @click="showImportDialog = true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
              <polyline points="17 8 12 3 7 8" />
              <line x1="12" y1="3" x2="12" y2="15" />
            </svg>
            导入技能包
          </button>
          <button class="btn btn--ghost" @click="handleReload">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M23 4v6h-6M1 20v-6h6" />
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
            </svg>
            重新加载
          </button>
        </div>
      </div>

      <div class="skill-manager__stats">
        <div class="stat-card">
          <div class="stat-card__icon stat-card__icon--primary">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
            </svg>
          </div>
          <div class="stat-card__content">
            <div class="stat-card__value">{{ stats.totalSkills }}</div>
            <div class="stat-card__label">总技能数</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-card__icon stat-card__icon--success">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
            </svg>
          </div>
          <div class="stat-card__content">
            <div class="stat-card__value">{{ stats.categories }}</div>
            <div class="stat-card__label">分类数</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-card__icon stat-card__icon--warning">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
            </svg>
          </div>
          <div class="stat-card__content">
            <div class="stat-card__value">{{ stats.totalExecutions }}</div>
            <div class="stat-card__label">总执行次数</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-card__icon stat-card__icon--info">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
              <polyline points="22 4 12 14.01 9 11.01" />
            </svg>
          </div>
          <div class="stat-card__content">
            <div class="stat-card__value">{{ (stats.averageSuccessRate * 100).toFixed(1) }}%</div>
            <div class="stat-card__label">平均成功率</div>
          </div>
        </div>
      </div>

      <div class="skill-manager__toolbar">
        <div class="search-box">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="搜索技能..."
            @input="handleSearch"
          />
        </div>
        <div class="tab-buttons">
          <button
            class="tab-btn"
            :class="{ 'tab-btn--active': activeTab === 'all' }"
            @click="activeTab = 'all'"
          >
            所有技能
          </button>
          <button
            class="tab-btn"
            :class="{ 'tab-btn--active': activeTab === 'category' }"
            @click="activeTab = 'category'"
          >
            按分类
          </button>
          <button
            class="tab-btn"
            :class="{ 'tab-btn--active': activeTab === 'suggest' }"
            @click="activeTab = 'suggest'"
          >
            推荐技能
          </button>
        </div>
      </div>

      <div class="skill-manager__content">
        <div v-if="activeTab === 'all'" class="skills-by-scope">
          <div v-for="scopeGroup in scopeGroups" :key="scopeGroup.scope" class="scope-section">
            <div class="scope-header" @click="toggleScope(scopeGroup.scope)">
              <div class="scope-header__left">
                <span class="scope-badge" :class="'scope-badge--' + scopeGroup.scope.toLowerCase()">
                  {{ scopeGroup.label }}
                </span>
                <span class="scope-count">{{ scopeGroup.skills.length }} 个技能</span>
              </div>
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                class="scope-arrow"
                :class="{ 'scope-arrow--open': expandedScopes.includes(scopeGroup.scope) }"
              >
                <polyline points="6 9 12 15 18 9" />
              </svg>
            </div>
            <div v-show="expandedScopes.includes(scopeGroup.scope)" class="skills-grid">
              <div
                v-for="skill in scopeGroup.skills"
                :key="skill.name"
                class="skill-card"
                :class="{ 'skill-card--selected': skill.isSelected }"
                @click="handleSkillClick(skill)"
              >
                <div class="skill-card__header">
                  <div class="skill-card__title">
                    <h3>{{ skill.name }}</h3>
                    <span class="priority-badge" :class="'priority-badge--' + skill.priority.toLowerCase()">
                      {{ skill.priority }}
                    </span>
                  </div>
                </div>

                <p class="skill-card__desc">{{ skill.description }}</p>

                <div class="skill-card__meta">
                  <div class="meta-item">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
                    </svg>
                    {{ skill.category }}
                  </div>
                  <div class="meta-item">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="12" cy="12" r="10" />
                      <polyline points="12 6 12 12 16 14" />
                    </svg>
                    {{ skill.averageExecutionTime }}ms
                  </div>
                </div>

                <div class="skill-card__tags" v-if="skill.tags?.length">
                  <span v-for="tag in skill.tags.slice(0, 3)" :key="tag" class="tag">{{ tag }}</span>
                  <span v-if="skill.tags.length > 3" class="tag">+{{ skill.tags.length - 3 }}</span>
                </div>

                <div class="skill-card__stats">
                  <div class="stat">
                    <span class="stat__label">执行次数</span>
                    <span class="stat__value">{{ skill.executionCount }}</span>
                  </div>
                  <div class="stat">
                    <span class="stat__label">成功率</span>
                    <span class="stat__value">{{ (skill.successRate * 100).toFixed(1) }}%</span>
                  </div>
                </div>

                <div class="skill-card__actions" @click.stop>
                  <label class="toggle-switch toggle-switch--small">
                    <input
                      type="checkbox"
                      :checked="skill.isSelected"
                      :disabled="skill.scope !== 'USER'"
                      @change="handleSelectionChange(skill, !skill.isSelected)"
                    />
                    <span class="toggle-slider"></span>
                  </label>
                  <span class="pending-badge" v-if="pendingChanges.has(skill.name)">待保存</span>
                  <button
                    v-if="skill.scope === 'USER'"
                    class="btn btn--small btn--danger"
                    @click="handleDelete(skill)"
                  >
                    删除
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'category'" class="category-view">
          <div
            v-for="(categorySkills, category) in skillsByCategory"
            :key="category"
            class="category-section"
          >
            <div class="category-header" @click="toggleCategory(category)">
              <h3>{{ category }}</h3>
              <span class="category-count">{{ categorySkills.length }}</span>
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                class="category-arrow"
                :class="{ 'category-arrow--open': activeCategories.includes(category) }"
              >
                <polyline points="6 9 12 15 18 9" />
              </svg>
            </div>
            <div class="category-content" v-if="activeCategories.includes(category)">
              <div
                v-for="skill in categorySkills"
                :key="skill.name"
                class="skill-card-mini"
                @click="handleSkillClick(skill)"
              >
                <div class="skill-card-mini__header">
                  <h4>{{ skill.name }}</h4>
                  <span class="priority-badge" :class="'priority-badge--' + skill.priority.toLowerCase()">
                    {{ skill.priority }}
                  </span>
                </div>
                <p class="skill-card-mini__desc">{{ skill.description }}</p>
              </div>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'suggest'" class="suggest-view">
          <div class="suggest-input">
            <textarea
              v-model="suggestRequest"
              placeholder="输入您的需求，系统将推荐合适的技能..."
              rows="3"
            ></textarea>
            <button class="btn btn--primary" @click="handleSuggest" :disabled="isSuggesting">
              {{ isSuggesting ? '获取中...' : '获取推荐' }}
            </button>
          </div>

          <div v-if="suggestedSkills.length > 0" class="suggested-skills">
            <h3>推荐技能</h3>
            <div
              v-for="skill in suggestedSkills"
              :key="skill.name"
              class="skill-card"
            >
              <div class="skill-card__header">
                <h3>{{ skill.name }}</h3>
              </div>
              <p class="skill-card__desc">{{ skill.description }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="showDetailDialog"
      :title="'技能详情: ' + currentSkill?.name"
      :width="isMobile ? '95%' : '900px'"
      class="skill-dialog"
    >
      <div v-if="currentSkill" class="skill-detail">
        <el-tabs v-model="editActiveTab">
          <el-tab-pane label="基本信息" name="basic">
            <el-form :model="currentSkill" :label-width="isMobile ? '80px' : '100px'">
              <el-form-item label="技能名称">
                <el-input v-model="currentSkill.name" disabled />
              </el-form-item>
              <el-form-item label="版本">
                <el-input v-model="currentSkill.version" disabled />
              </el-form-item>
              <el-form-item label="分类">
                <el-input v-model="currentSkill.category" disabled />
              </el-form-item>
              <el-form-item label="优先级">
                <el-input :model-value="priorityLabel(currentSkill.priority)" disabled />
              </el-form-item>
              <el-form-item label="描述">
                <el-input
                  v-model="currentSkill.description"
                  type="textarea"
                  :rows="2"
                  :disabled="!canEditSkill"
                />
              </el-form-item>
              <el-form-item label="标签">
                <div class="preview-tags" v-if="!canEditSkill && currentSkill.tags?.length">
                  <span v-for="tag in currentSkill.tags" :key="tag" class="tag">{{ tag }}</span>
                </div>
                <el-select
                  v-else-if="canEditSkill"
                  v-model="currentSkill.tags"
                  multiple
                  filterable
                  allow-create
                  placeholder="选择或创建标签"
                  class="input-full"
                />
                <span v-else class="preview-empty">无</span>
              </el-form-item>
              <el-form-item label="所需工具">
                <div class="preview-tags" v-if="!canEditSkill && currentSkill.allowedTools?.length">
                  <span v-for="tool in currentSkill.allowedTools" :key="tool" class="tag">{{ tool }}</span>
                </div>
                <el-select
                  v-else-if="canEditSkill"
                  v-model="currentSkill.allowedTools"
                  multiple
                  filterable
                  placeholder="选择所需工具"
                  class="input-full"
                />
                <span v-else class="preview-empty">无</span>
              </el-form-item>
              <el-form-item label="指令">
                <el-input
                  v-model="currentSkill.instructions"
                  type="textarea"
                  :rows="8"
                  :disabled="!canEditSkill"
                />
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="脚本" name="scripts">
            <div class="scripts-section">
              <div class="scripts-header section-gap">
                <h4>脚本列表</h4>
                <div class="scripts-actions">
                  <el-upload
                    v-if="canEditSkill"
                    :show-file-list="false"
                    :before-upload="(file: File) => handleScriptUpload(file, true)"
                    accept=".py,.sh,.js,.ts,.rb,.ps1"
                  >
                    <el-button type="primary" size="small">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                        <polyline points="17 8 12 3 7 8" />
                        <line x1="12" y1="3" x2="12" y2="15" />
                      </svg>
                      上传脚本
                    </el-button>
                  </el-upload>
                  <el-dropdown v-if="canEditSkill" trigger="click" @command="handleEditScriptFromTemplate">
                    <el-button type="default" size="small">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                        <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                        <line x1="3" y1="9" x2="21" y2="9" />
                        <line x1="9" y1="21" x2="9" y2="9" />
                      </svg>
                      从模板创建
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="python">Python 脚本模板</el-dropdown-item>
                        <el-dropdown-item command="bash">Bash 脚本模板</el-dropdown-item>
                        <el-dropdown-item command="javascript">JavaScript 脚本模板</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                  <el-button v-if="canEditSkill" type="success" size="small" @click="addNewScriptToExisting">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                      <line x1="12" y1="5" x2="12" y2="19" />
                      <line x1="5" y1="12" x2="19" y2="12" />
                    </svg>
                    新建脚本
                  </el-button>
                </div>
              </div>
              <div v-if="currentSkillScripts.length === 0" class="empty-hint">
                <div class="empty-hint__icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="16 18 22 12 16 6" />
                    <polyline points="8 6 2 12 8 18" />
                  </svg>
                </div>
                <p>暂无脚本</p>
                <p v-if="canEditSkill" class="empty-hint__sub">上传脚本文件、从模板创建或新建空白脚本</p>
              </div>
              <div v-for="(script, index) in currentSkillScripts" :key="index" class="script-item">
                <div class="script-item__header">
                  <div class="script-item__name">
                    <svg class="script-icon" :class="'script-icon--' + script.scriptType" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="16 18 22 12 16 6" />
                      <polyline points="8 6 2 12 8 18" />
                    </svg>
                    <el-input v-model="script.scriptName" placeholder="脚本名称" class="input-medium" :disabled="!canEditSkill" />
                  </div>
                  <div class="script-item__type">
                    <el-select v-model="script.scriptType" class="input-small" :disabled="!canEditSkill">
                      <el-option label="Python" value="python">
                        <span class="script-type-option">
                          <span class="script-type-dot script-type-dot--python"></span>
                          Python
                        </span>
                      </el-option>
                      <el-option label="Bash" value="bash">
                        <span class="script-type-option">
                          <span class="script-type-dot script-type-dot--bash"></span>
                          Bash
                        </span>
                      </el-option>
                      <el-option label="JavaScript" value="javascript">
                        <span class="script-type-option">
                          <span class="script-type-dot script-type-dot--javascript"></span>
                          JavaScript
                        </span>
                      </el-option>
                      <el-option label="TypeScript" value="typescript">
                        <span class="script-type-option">
                          <span class="script-type-dot script-type-dot--typescript"></span>
                          TypeScript
                        </span>
                      </el-option>
                      <el-option label="Ruby" value="ruby">
                        <span class="script-type-option">
                          <span class="script-type-dot script-type-dot--ruby"></span>
                          Ruby
                        </span>
                      </el-option>
                    </el-select>
                  </div>
                  <div class="script-item__actions">
                    <el-button v-if="canEditSkill" type="danger" size="small" link @click="removeExistingScript(index)">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                        <polyline points="3 6 5 6 21 6" />
                        <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                      </svg>
                      删除
                    </el-button>
                  </div>
                </div>
                <div class="script-editor">
                  <div class="script-editor__toolbar">
                    <span class="line-count">{{ getLineCount(script.scriptContent) }} 行</span>
                    <span class="char-count">{{ script.scriptContent?.length || 0 }} 字符</span>
                    <span v-if="script.isModified" class="modified-badge">已修改</span>
                  </div>
                  <el-input
                    v-model="script.scriptContent"
                    type="textarea"
                    :rows="12"
                    :disabled="!canEditSkill"
                    :class="['script-code', { 'script-code--editable': canEditSkill }]"
                    @input="markScriptModified(script)"
                  />
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="资源" name="resources">
            <div class="resources-section">
              <!-- 资源操作栏 -->
              <div class="resources-header section-gap">
                <h4>资源列表</h4>
                <div class="resources-actions" v-if="canEditSkill">
                  <el-radio-group v-model="editResourceUploadType" size="small">
                    <el-radio-button value="document">文档</el-radio-button>
                    <el-radio-button value="image">图片</el-radio-button>
                    <el-radio-button value="other">其他</el-radio-button>
                  </el-radio-group>
                  <el-button type="primary" size="small" @click="triggerEditFileInput">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                      <polyline points="17 8 12 3 7 8" />
                      <line x1="12" y1="3" x2="12" y2="15" />
                    </svg>
                    上传资源
                  </el-button>
                </div>
              </div>

              <!-- 隐藏的文件输入 -->
              <input
                ref="editFileInputRef"
                type="file"
                :accept="editAcceptTypes"
                multiple
                style="display: none"
                @change="handleEditFileSelect"
              />

              <!-- 拖拽上传区域 -->
              <div
                v-if="canEditSkill"
                class="upload-dropzone"
                :class="{ 'upload-dropzone--dragover': isEditDragover }"
                @dragover.prevent="isEditDragover = true"
                @dragleave.prevent="isEditDragover = false"
                @drop.prevent="handleEditDropUpload"
                @click="triggerEditFileInput"
              >
                <div class="upload-dropzone__content">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="upload-icon">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                    <polyline points="17 8 12 3 7 8" />
                    <line x1="12" y1="3" x2="12" y2="15" />
                  </svg>
                  <p class="upload-text">拖拽文件到此处，或点击上传</p>
                  <p class="upload-hint">支持图片、文档等多种格式</p>
                </div>
              </div>

              <!-- 图片预览网格 -->
              <div v-if="imageResources.length > 0" class="image-preview-grid">
                <div v-for="(resource, index) in imageResources" :key="index" class="image-preview-item">
                  <img :src="getResourcePreviewUrl(resource)" :alt="resource.resourceName" />
                  <div class="image-preview-overlay">
                    <span class="image-name">{{ resource.resourceName }}</span>
                    <div class="image-actions">
                      <el-button type="primary" size="small" link @click="handleDownloadResource(resource)">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                          <polyline points="7 10 12 15 17 10" />
                          <line x1="12" y1="15" x2="12" y2="3" />
                        </svg>
                      </el-button>
                      <el-button v-if="canEditSkill" type="danger" size="small" link @click="removeExistingResource(resource)">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                          <polyline points="3 6 5 6 21 6" />
                          <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                        </svg>
                      </el-button>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 非图片资源列表 -->
              <div v-if="nonImageResources.length > 0" class="resources-list">
                <div v-for="(resource, index) in nonImageResources" :key="index" class="resource-item">
                  <div class="resource-item__icon">
                    <svg v-if="isDocumentType(resource)" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                      <polyline points="14 2 14 8 20 8" />
                      <line x1="16" y1="13" x2="8" y2="13" />
                      <line x1="16" y1="17" x2="8" y2="17" />
                      <polyline points="10 9 9 9 8 9" />
                    </svg>
                    <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z" />
                      <polyline points="13 2 13 9 20 9" />
                    </svg>
                  </div>
                  <div class="resource-item__info">
                    <span class="resource-name">{{ resource.resourceName }}</span>
                    <span class="resource-meta">{{ formatFileSize(resource.fileSize || 0) }} · {{ resource.resourceType }}</span>
                  </div>
                  <div class="resource-item__actions">
                    <el-button type="primary" size="small" link @click="handleDownloadResource(resource)">下载</el-button>
                    <el-button v-if="canEditSkill" type="danger" size="small" link @click="removeExistingResource(resource)">删除</el-button>
                  </div>
                </div>
              </div>

              <div v-if="currentSkillResources.length === 0" class="empty-hint">
                <p>暂无资源</p>
                <p v-if="canEditSkill" class="empty-hint__sub">点击上方按钮上传资源</p>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
      <template #footer>
        <el-button @click="showDetailDialog = false">关闭</el-button>
        <el-button v-if="canEditSkill && hasDetailChanges" type="primary" :loading="isSavingDetail" @click="saveDetailChanges">
          保存修改
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="showCreateDialog"
      title="创建新技能"
      :width="isMobile ? '95%' : '800px'"
      class="skill-dialog"
    >
      <el-tabs v-model="createActiveTab">
        <el-tab-pane label="基本信息" name="basic">
          <el-form :model="newSkill" :label-width="isMobile ? '80px' : '100px'">
            <el-form-item label="技能名称">
              <el-input v-model="newSkill.name" placeholder="输入技能名称" />
            </el-form-item>
            <el-form-item label="描述">
              <el-input
                v-model="newSkill.description"
                type="textarea"
                :rows="2"
                placeholder="输入技能描述"
              />
            </el-form-item>
            <el-form-item label="分类">
              <el-input v-model="newSkill.category" placeholder="输入分类名称" />
            </el-form-item>
            <el-form-item label="标签">
              <el-select
                v-model="newSkill.tags"
                multiple
                filterable
                allow-create
                placeholder="选择或创建标签"
                class="input-full"
              />
            </el-form-item>
            <el-form-item label="所需工具">
              <el-select
                v-model="newSkill.allowedTools"
                multiple
                filterable
                placeholder="选择所需工具"
                class="input-full"
              >
                <el-option
                  v-for="tool in availableTools"
                  :key="tool"
                  :label="tool"
                  :value="tool"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="指令">
              <el-input
                v-model="newSkill.instructions"
                type="textarea"
                :rows="5"
                placeholder="输入技能执行指令"
              />
            </el-form-item>
            <el-form-item label="优先级">
              <el-select v-model="newSkill.priority" class="input-full">
                <el-option label="低" value="LOW" />
                <el-option label="中" value="MEDIUM" />
                <el-option label="高" value="HIGH" />
                <el-option label="关键" value="CRITICAL" />
              </el-select>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        
        <el-tab-pane label="脚本" name="scripts">
          <div class="scripts-section">
            <div class="scripts-header">
              <h4>脚本列表</h4>
              <div class="scripts-actions">
                <el-dropdown trigger="click" @command="handleAddNewScriptFromTemplate">
                  <el-button type="default" size="small">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                      <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                      <line x1="3" y1="9" x2="21" y2="9" />
                      <line x1="9" y1="21" x2="9" y2="9" />
                    </svg>
                    从模板创建
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="python">Python 脚本模板</el-dropdown-item>
                      <el-dropdown-item command="bash">Bash 脚本模板</el-dropdown-item>
                      <el-dropdown-item command="javascript">JavaScript 脚本模板</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <el-upload
                  :show-file-list="false"
                  :before-upload="(file: File) => handleScriptUpload(file, false)"
                  accept=".py,.sh,.js,.ts,.rb,.ps1"
                >
                  <el-button type="primary" size="small">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                      <polyline points="17 8 12 3 7 8" />
                      <line x1="12" y1="3" x2="12" y2="15" />
                    </svg>
                    上传脚本文件
                  </el-button>
                </el-upload>
                <el-button type="success" size="small" @click="addNewScript">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                    <line x1="12" y1="5" x2="12" y2="19" />
                    <line x1="5" y1="12" x2="19" y2="12" />
                  </svg>
                  新建空白脚本
                </el-button>
              </div>
            </div>
            <div v-if="newSkillScripts.length === 0" class="empty-hint">
              <div class="empty-hint__icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="16 18 22 12 16 6" />
                  <polyline points="8 6 2 12 8 18" />
                </svg>
              </div>
              <p>暂无脚本</p>
              <p class="empty-hint__sub">上传脚本文件、从模板创建或新建空白脚本</p>
            </div>
            <div v-for="(script, index) in newSkillScripts" :key="index" class="script-item">
              <div class="script-item__header">
                <div class="script-item__name">
                  <svg class="script-icon" :class="'script-icon--' + script.scriptType" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="16 18 22 12 16 6" />
                    <polyline points="8 6 2 12 8 18" />
                  </svg>
                  <el-input v-model="script.scriptName" placeholder="脚本名称" class="input-medium" />
                </div>
                <div class="script-item__type">
                  <el-select v-model="script.scriptType" class="input-small">
                    <el-option label="Python" value="python">
                      <span class="script-type-option">
                        <span class="script-type-dot script-type-dot--python"></span>
                        Python
                      </span>
                    </el-option>
                    <el-option label="Bash" value="bash">
                      <span class="script-type-option">
                        <span class="script-type-dot script-type-dot--bash"></span>
                        Bash
                      </span>
                    </el-option>
                    <el-option label="JavaScript" value="javascript">
                      <span class="script-type-option">
                        <span class="script-type-dot script-type-dot--javascript"></span>
                        JavaScript
                      </span>
                    </el-option>
                    <el-option label="TypeScript" value="typescript">
                      <span class="script-type-option">
                        <span class="script-type-dot script-type-dot--typescript"></span>
                        TypeScript
                      </span>
                    </el-option>
                    <el-option label="Ruby" value="ruby">
                      <span class="script-type-option">
                        <span class="script-type-dot script-type-dot--ruby"></span>
                        Ruby
                      </span>
                    </el-option>
                  </el-select>
                </div>
                <div class="script-item__actions">
                  <el-button type="danger" size="small" link @click="removeNewScript(index)">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                      <polyline points="3 6 5 6 21 6" />
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                    </svg>
                    删除
                  </el-button>
                </div>
              </div>
              <div class="script-editor">
                <div class="script-editor__toolbar">
                  <span class="line-count">{{ getLineCount(script.scriptContent) }} 行</span>
                  <span class="char-count">{{ script.scriptContent?.length || 0 }} 字符</span>
                </div>
                <el-input
                  v-model="script.scriptContent"
                  type="textarea"
                  :rows="12"
                  :placeholder="getScriptPlaceholder(script.scriptType)"
                  class="script-code"
                />
              </div>
            </div>
          </div>
        </el-tab-pane>
        
        <el-tab-pane label="资源" name="resources">
          <div class="resources-section">
            <div class="resources-header">
              <h4>资源列表</h4>
              <div class="resource-type-selector">
                <el-radio-group v-model="newResourceUploadType" size="small">
                  <el-radio-button value="document">文档</el-radio-button>
                  <el-radio-button value="image">图片</el-radio-button>
                  <el-radio-button value="other">其他</el-radio-button>
                </el-radio-group>
              </div>
            </div>

            <!-- 拖拽上传区域 -->
            <div
              class="upload-dropzone"
              :class="{ 'upload-dropzone--dragover': isNewDragover }"
              @dragover.prevent="isNewDragover = true"
              @dragleave.prevent="isNewDragover = false"
              @drop.prevent="handleNewDropUpload"
              @click="triggerNewFileInput"
            >
              <input
                ref="newFileInputRef"
                type="file"
                :accept="newAcceptTypes"
                multiple
                style="display: none"
                @change="handleNewFileSelect"
              />
              <div class="upload-dropzone__content">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="upload-icon">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                  <polyline points="17 8 12 3 7 8" />
                  <line x1="12" y1="3" x2="12" y2="15" />
                </svg>
                <p class="upload-text">拖拽文件到此处，或 <span class="upload-link">点击上传</span></p>
                <p class="upload-hint">{{ newUploadHintText }}</p>
              </div>
            </div>

            <!-- 图片预览网格 -->
            <div v-if="newImageResources.length > 0" class="image-preview-grid">
              <div v-for="(resource, index) in newImageResources" :key="index" class="image-preview-item">
                <img :src="getResourcePreviewUrl(resource)" :alt="resource.resourceName" />
                <div class="image-preview-overlay">
                  <span class="image-name">{{ resource.resourceName }}</span>
                  <el-button type="danger" size="small" link @click="removeNewSkillResource(getNewResourceIndex(resource))">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                      <polyline points="3 6 5 6 21 6" />
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                    </svg>
                  </el-button>
                </div>
              </div>
            </div>

            <!-- 非图片资源列表 -->
            <div v-if="newNonImageResources.length > 0" class="resources-list">
              <div v-for="(resource, index) in newNonImageResources" :key="index" class="resource-item">
                <div class="resource-item__icon">
                  <svg v-if="isDocumentType(resource)" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                    <polyline points="14 2 14 8 20 8" />
                    <line x1="16" y1="13" x2="8" y2="13" />
                    <line x1="16" y1="17" x2="8" y2="17" />
                    <polyline points="10 9 9 9 8 9" />
                  </svg>
                  <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z" />
                    <polyline points="13 2 13 9 20 9" />
                  </svg>
                </div>
                <div class="resource-item__info">
                  <span class="resource-name">{{ resource.resourceName }}</span>
                  <span class="resource-meta">{{ formatFileSize(resource.fileSize || 0) }} · {{ resource.resourceType }}</span>
                </div>
                <div class="resource-item__actions">
                  <el-button type="danger" size="small" link @click="removeNewSkillResource(getNewResourceIndex(resource))">删除</el-button>
                </div>
              </div>
            </div>

            <div v-if="newSkillResources.length === 0" class="empty-hint">
              暂无资源，拖拽文件或点击上方区域上传
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="isCreating" @click="handleCreate">
          创建
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="showImportDialog"
      title="导入技能包"
      :width="isMobile ? '95%' : '560px'"
      class="skill-dialog"
    >
      <div class="import-dialog__content">
        <div
          class="upload-dropzone"
          :class="{ 'upload-dropzone--active': isImportDragover, 'upload-dropzone--disabled': isImporting }"
          @dragover.prevent="isImportDragover = true"
          @dragleave.prevent="isImportDragover = false"
          @drop.prevent="handleImportDrop"
          @click="!isImporting && importFileInputRef?.click()"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="upload-dropzone__icon">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
            <polyline points="17 8 12 3 7 8" />
            <line x1="12" y1="3" x2="12" y2="15" />
          </svg>
          <p class="upload-dropzone__text">
            {{ isImporting ? '导入中...' : '拖拽 .zip 文件到此处，或点击选择文件' }}
          </p>
          <p class="upload-dropzone__hint">仅支持 .zip 格式，最大 10MB，包内需包含 SKILL.md</p>
        </div>
        <input
          ref="importFileInputRef"
          type="file"
          accept=".zip"
          style="display: none"
          @change="handleImportFileSelect"
        />

        <div v-if="importResult" class="import-result" :class="{ 'import-result--success': importResult.success, 'import-result--error': !importResult.success }">
          <div class="import-result__header">
            <svg v-if="importResult.success" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
              <polyline points="22 4 12 14.01 9 11.01" />
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10" />
              <line x1="15" y1="9" x2="9" y2="15" />
              <line x1="9" y1="9" x2="15" y2="15" />
            </svg>
            <span>{{ importResult.success ? '导入成功' : '导入失败' }}</span>
          </div>
          <p class="import-result__message">{{ importResult.message }}</p>
          <div v-if="importResult.success" class="import-result__details">
            <span v-if="importResult.skillName">技能名称: {{ importResult.skillName }}</span>
            <span v-if="importResult.extractedFiles">提取文件: {{ importResult.extractedFiles }} 个</span>
            <span v-if="importResult.resourceCount">资源上传: {{ importResult.resourceCount }} 个</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showImportDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useSkillStore } from '@/stores/skill'
import { uploadResource, downloadResource, importSkillPackage } from '@/api/skill'
import type { Skill, SkillConfig, SkillScript, SkillResource, SkillImportResult } from '@/types/skill'

const skillStore = useSkillStore()

const windowWidth = ref(window.innerWidth)
const isMobile = computed(() => windowWidth.value <= 768)

const handleResize = () => {
  windowWidth.value = window.innerWidth
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})

const searchKeyword = ref('')
const activeTab = ref('all')
const activeCategories = ref<string[]>([])
const expandedScopes = ref<string[]>(['USER', 'PROJECT'])
const showDetailDialog = ref(false)
const showCreateDialog = ref(false)
const showImportDialog = ref(false)
const isImporting = ref(false)
const isImportDragover = ref(false)
const importFileInputRef = ref<HTMLInputElement | null>(null)
const importResult = ref<SkillImportResult | null>(null)

watch(showImportDialog, (val) => {
  if (val) {
    importResult.value = null
  }
})
const currentSkill = ref<Skill | null>(null)
const originalSkillName = ref('')
const originalSkillData = ref<Skill | null>(null) // 保存原始技能数据用于检测变化
const suggestRequest = ref('')
const isSuggesting = ref(false)
const isCreating = ref(false)
const isSaving = ref(false)
const pendingChanges = ref<Map<string, boolean>>(new Map())
const editActiveTab = ref('basic')
const currentSkillScripts = ref<SkillScript[]>([])
const currentSkillResources = ref<SkillResource[]>([])
const isSavingDetail = ref(false)
const editResourceUploadType = ref<'document' | 'image' | 'other'>('document')
const editFileInputRef = ref<HTMLInputElement | null>(null)
const isEditDragover = ref(false)

// 是否可以编辑技能（只有用户技能可以编辑）
const canEditSkill = computed(() => {
  return currentSkill.value?.scope === 'USER'
})

// 检测基本信息是否有变化
const hasBasicInfoChanged = computed(() => {
  if (!originalSkillData.value || !currentSkill.value) return false
  return originalSkillData.value.description !== currentSkill.value.description ||
    originalSkillData.value.instructions !== currentSkill.value.instructions ||
    JSON.stringify(originalSkillData.value.tags) !== JSON.stringify(currentSkill.value.tags) ||
    JSON.stringify(originalSkillData.value.allowedTools) !== JSON.stringify(currentSkill.value.allowedTools)
})

// 是否有未保存的修改
const hasDetailChanges = computed(() => {
  return hasBasicInfoChanged.value ||
    currentSkillScripts.value.some(s => s.isModified)
})

// 创建技能资源上传相关
const newResourceUploadType = ref<'document' | 'image' | 'other'>('document')
const isNewDragover = ref(false)
const newFileInputRef = ref<HTMLInputElement | null>(null)

const stats = computed(() => skillStore.stats)
const skills = computed(() => skillStore.skills)
const skillsByCategory = computed(() => skillStore.skillsByCategory)
const suggestedSkills = computed(() => skillStore.suggestedSkills)

// 资源上传类型对应的 accept 和提示
const acceptTypeMap = {
  document: {
    accept: '.pdf,.doc,.docx,.txt,.md,.json,.csv,.xlsx,.xls,.ppt,.pptx',
    hint: '支持 PDF、Word、Excel、TXT、Markdown、JSON 等文档格式'
  },
  image: {
    accept: '.jpg,.jpeg,.png,.gif,.webp,.svg,.bmp,.ico',
    hint: '支持 JPG、PNG、GIF、WebP、SVG 等图片格式'
  },
  other: {
    accept: '*',
    hint: '支持所有文件类型'
  }
}

const newAcceptTypes = computed(() => acceptTypeMap[newResourceUploadType.value].accept)
const newUploadHintText = computed(() => acceptTypeMap[newResourceUploadType.value].hint)
const editAcceptTypes = computed(() => acceptTypeMap[editResourceUploadType.value].accept)

// 图片资源过滤
const imageResources = computed(() => {
  return currentSkillResources.value.filter(r => isImageType(r))
})

const nonImageResources = computed(() => {
  return currentSkillResources.value.filter(r => !isImageType(r))
})

const newImageResources = computed(() => {
  return newSkillResources.value.filter(r => isImageType(r))
})

const newNonImageResources = computed(() => {
  return newSkillResources.value.filter(r => !isImageType(r))
})

const displaySkills = computed(() => {
  if (searchKeyword.value) {
    return skillStore.searchResults
  }
  return skills.value
})

const scopeGroups = computed(() => {
  const groups: { scope: string; label: string; skills: Skill[] }[] = [
    { scope: 'USER', label: '用户技能', skills: [] },
    { scope: 'PROJECT', label: '项目技能', skills: [] },
    { scope: 'SYSTEM', label: '系统技能', skills: [] }
  ]
  
  const skillList = displaySkills.value
  for (const skill of skillList) {
    const group = groups.find(g => g.scope === skill.scope)
    if (group) {
      group.skills.push(skill)
    }
  }
  
  return groups.filter(g => g.skills.length > 0)
})

const availableTools = ref<string[]>([])

const newSkill = ref<SkillConfig>({
  name: '',
  description: '',
  category: '',
  version: '1.0.0',
  tags: [],
  allowedTools: [],
  instructions: '',
  priority: 'MEDIUM'
})

const createActiveTab = ref('basic')
const newSkillScripts = ref<SkillScript[]>([])
const newSkillResources = ref<SkillResource[]>([])

// 脚本模板
const scriptTemplates = {
  python: `#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
技能脚本: ${newSkill.value?.name || 'skill_name'}
描述: 在此编写脚本功能说明
"""

import json
import sys
from typing import Dict, Any

def main(params: Dict[str, Any]) -> Dict[str, Any]:
    """
    主函数
    :param params: 输入参数
    :return: 执行结果
    """
    result = {
        "success": True,
        "data": {},
        "message": "脚本执行成功"
    }

    try:
        # 在此编写业务逻辑
        print(f"收到参数: {params}")

        # 示例: 处理数据
        # data = params.get("data", {})
        # result["data"] = process_data(data)

    except Exception as e:
        result["success"] = False
        result["message"] = f"执行失败: {str(e)}"

    return result

if __name__ == "__main__":
    # 从标准输入读取参数
    input_params = json.loads(sys.stdin.read()) if sys.stdin.read() else {}
    output = main(input_params)
    print(json.dumps(output, ensure_ascii=False, indent=2))
`,
  bash: `#!/bin/bash
# 技能脚本: ${newSkill.value?.name || 'skill_name'}
# 描述: 在此编写脚本功能说明

set -e

# 参数处理
PARAMS="\${1:-}"

echo "开始执行脚本..."
echo "参数: \$PARAMS"

# 在此编写业务逻辑
# 示例:
# if [ -n "\$PARAMS" ]; then
#     echo "处理参数: \$PARAMS"
# fi

echo "脚本执行完成"

# 返回 JSON 格式结果
echo '{"success": true, "message": "脚本执行成功"}'
`,
  javascript: `/**
 * 技能脚本: ${newSkill.value?.name || 'skill_name'}
 * 描述: 在此编写脚本功能说明
 */

/**
 * 主函数
 * @param {Object} params - 输入参数
 * @returns {Object} 执行结果
 */
function main(params) {
    const result = {
        success: true,
        data: {},
        message: '脚本执行成功'
    };

    try {
        console.log('收到参数:', params);

        // 在此编写业务逻辑
        // const data = params.data || {};
        // result.data = processData(data);

    } catch (error) {
        result.success = false;
        result.message = \`执行失败: \${error.message}\`;
    }

    return result;
}

// 从命令行参数或标准输入获取参数
const params = process.argv[2] ? JSON.parse(process.argv[2]) : {};
const output = main(params);
console.log(JSON.stringify(output, null, 2));
`
}

onMounted(async () => {
  await loadInitialData()
})

const loadInitialData = async () => {
  try {
    // 强制刷新技能列表，确保获取最新的用户技能
    await Promise.all([
      skillStore.loadAllSkills(true),
      skillStore.loadCategories(),
      skillStore.loadTags(),
      skillStore.loadStats()
    ])
  } catch (error) {
    ElMessage.error('加载数据失败')
    console.error(error)
  }
}

const handleSearch = async () => {
  if (searchKeyword.value.trim()) {
    await skillStore.search(searchKeyword.value)
  } else {
    skillStore.clearSearchResults()
  }
}

const handleReload = async () => {
  try {
    await skillStore.reloadSkills()
    ElMessage.success('重新加载成功')
  } catch (error) {
    ElMessage.error('重新加载失败')
    console.error(error)
  }
}

const handleImportDrop = (e: DragEvent) => {
  isImportDragover.value = false
  if (isImporting.value) return
  const files = e.dataTransfer?.files
  if (files && files.length > 0) {
    processImportFile(files[0])
  }
}

const handleImportFileSelect = (e: Event) => {
  const input = e.target as HTMLInputElement
  if (input.files && input.files.length > 0) {
    processImportFile(input.files[0])
  }
  input.value = ''
}

const processImportFile = async (file: File) => {
  if (!file.name.endsWith('.zip')) {
    ElMessage.warning('请选择 .zip 格式的技能包文件')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('文件大小不能超过 10MB')
    return
  }

  try {
    isImporting.value = true
    importResult.value = null
    const response = await importSkillPackage(file)
    importResult.value = response.data as SkillImportResult
    if (importResult.value?.success) {
      await skillStore.loadAllSkills()
      await skillStore.loadStats()
    }
  } catch (error) {
    importResult.value = { success: false, message: '导入失败，请检查文件格式' }
    console.error(error)
  } finally {
    isImporting.value = false
  }
}

const handleSkillClick = (skill: Skill) => {
  currentSkill.value = JSON.parse(JSON.stringify(skill))
  originalSkillName.value = skill.name
  originalSkillData.value = JSON.parse(JSON.stringify(skill)) // 保存原始数据用于检测变化
  editActiveTab.value = 'basic'
  currentSkillScripts.value = (skill as any).scripts || []
  currentSkillResources.value = (skill as any).resources || []
  showDetailDialog.value = true
}

const priorityLabel = (priority: string) => {
  const map: Record<string, string> = { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '关键' }
  return map[priority] || priority
}

const handleDelete = async (skill: Skill) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除技能 "${skill.name}" 吗？`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await skillStore.deleteSkill(skill.name)
    ElMessage.success('删除成功')
  } catch (error: unknown) {
    if (error !== 'cancel') {
      const errorMessage = error instanceof Error ? error.message : '删除失败'
      ElMessage.error(errorMessage)
      console.error(error)
    }
  }
}

const handleSelectionChange = (skill: Skill, isSelected: boolean) => {
  pendingChanges.value.set(skill.name, isSelected)
}

const hasPendingChanges = computed(() => pendingChanges.value.size > 0)

const saveAllChanges = async () => {
  if (pendingChanges.value.size === 0) {
    ElMessage.info('没有需要保存的修改')
    return
  }

  try {
    isSaving.value = true
    const changes = Array.from(pendingChanges.value.entries())
    
    await Promise.all(
      changes.map(([skillName, isSelected]) => 
        skillStore.updateSkillSelection(skillName, isSelected)
      )
    )
    
    ElMessage.success(`已保存 ${changes.length} 个技能选择状态的修改`)
    pendingChanges.value.clear()
  } catch (error) {
    ElMessage.error('保存失败')
    console.error(error)
  } finally {
    isSaving.value = false
  }
}

const discardChanges = () => {
  pendingChanges.value.clear()
  ElMessage.info('已放弃所有修改')
}

const handleSuggest = async () => {
  if (!suggestRequest.value.trim()) {
    ElMessage.warning('请输入您的需求')
    return
  }

  try {
    isSuggesting.value = true
    await skillStore.suggest(suggestRequest.value)
  } catch (error) {
    ElMessage.error('获取推荐失败')
    console.error(error)
  } finally {
    isSuggesting.value = false
  }
}

const handleScriptUpload = async (file: File, isEdit: boolean): Promise<boolean> => {
  const scriptType = getScriptTypeFromFile(file.name)
  const scriptName = file.name.replace(/\.[^.]+$/, '')

  try {
    const content = await readFileContent(file)

    if (isEdit && currentSkill.value) {
      // 实时调用 API 保存到后端
      const userStoreModule = await import('@/stores/user')
      const userStoreInstance = userStoreModule.useUserStore()
      const userId = userStoreInstance.user?.id ? Number(userStoreInstance.user.id) : undefined

      await skillStore.updateSkillScripts(
        currentSkill.value.name,
        scriptType,
        content,
        userId
      )
      ElMessage.success(`脚本 "${scriptName}" 上传成功`)
    } else {
      // 创建技能时的脚本，仅添加到本地列表
      const script = {
        scriptName,
        scriptType,
        scriptContent: content
      }
      newSkillScripts.value.push(script)
      ElMessage.success(`脚本 "${scriptName}" 上传成功`)
    }
  } catch (error) {
    ElMessage.error('上传脚本失败')
    console.error(error)
  }
  return false
}

// 从文件名获取脚本类型
const getScriptTypeFromFile = (filename: string): 'python' | 'bash' | 'javascript' | 'typescript' | 'ruby' | 'powershell' => {
  const ext = filename.split('.').pop()?.toLowerCase()
  const typeMap: Record<string, 'python' | 'bash' | 'javascript' | 'typescript' | 'ruby' | 'powershell'> = {
    'py': 'python',
    'sh': 'bash',
    'js': 'javascript',
    'ts': 'typescript',
    'rb': 'ruby',
    'ps1': 'powershell'
  }
  return typeMap[ext || ''] || 'python'
}

// 读取文件内容
const readFileContent = (file: File): Promise<string> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = () => reject(new Error('读取文件失败'))
    reader.readAsText(file)
  })
}

// 获取行数
const getLineCount = (content: string | undefined): number => {
  if (!content) return 0
  return content.split('\n').length
}

// 获取脚本占位符
const getScriptPlaceholder = (type: string): string => {
  const placeholders: Record<string, string> = {
    'python': '# 在此编写 Python 代码\nimport json\n\ndef main(params):\n    # 处理逻辑\n    return {"success": True}',
    'bash': '#!/bin/bash\n# 在此编写 Bash 脚本\n\necho "Hello World"',
    'javascript': '// 在此编写 JavaScript 代码\n\nfunction main(params) {\n  return { success: true };\n}',
    'typescript': '// 在此编写 TypeScript 代码\n\nfunction main(params: any): object {\n  return { success: true };\n}',
    'ruby': '# 在此编写 Ruby 代码\n\ndef main(params)\n  { success: true }\nend'
  }
  return placeholders[type] || '在此编写代码'
}

// 预览脚本（保留供将来使用）
const _previewScript = (script: SkillScript) => {
  ElMessageBox.alert(script.scriptContent || '无内容', `脚本预览: ${script.scriptName}`, {
    confirmButtonText: '关闭',
    customClass: 'script-preview-dialog'
  })
}
void _previewScript

// 标记脚本已修改
const markScriptModified = (script: SkillScript) => {
  script.isModified = true
}

// 为现有技能添加新脚本
const addNewScriptToExisting = () => {
  currentSkillScripts.value.push({
    scriptName: `script_${currentSkillScripts.value.length + 1}`,
    scriptType: 'python',
    scriptContent: '',
    isModified: true
  } as SkillScript & { isModified?: boolean })
}

// 删除现有脚本
const removeExistingScript = (index: number) => {
  currentSkillScripts.value.splice(index, 1)
}

// 从模板创建编辑中的脚本
const handleEditScriptFromTemplate = (type: string) => {
  const template = scriptTemplates[type as keyof typeof scriptTemplates] || ''
  currentSkillScripts.value.push({
    scriptName: `${type}_script_${currentSkillScripts.value.length + 1}`,
    scriptType: type as any,
    scriptContent: template,
    isModified: true
  } as SkillScript & { isModified?: boolean })
  ElMessage.success(`已创建 ${type} 脚本模板`)
}

// 删除现有资源
const removeExistingResource = async (resource: SkillResource) => {
  if (!currentSkill.value) return

  try {
    const userStoreModule = await import('@/stores/user')
    const userStoreInstance = userStoreModule.useUserStore()
    const userId = userStoreInstance.user?.id ? Number(userStoreInstance.user.id) : undefined

    const { deleteResource: deleteResourceApi } = await import('@/api/skill')
    const result = await deleteResourceApi(currentSkill.value.name, resource.resourcePath || '', userId)

    if (result.success) {
      // 从列表中移除
      const index = currentSkillResources.value.indexOf(resource)
      if (index > -1) {
        currentSkillResources.value.splice(index, 1)
      }
      ElMessage.success('删除成功')
    } else {
      ElMessage.error(result.error || '删除失败')
    }
  } catch (error) {
    ElMessage.error('删除失败')
    console.error(error)
  }
}

// 触发编辑资源上传
const triggerEditFileInput = () => {
  editFileInputRef.value?.click()
}

// 处理编辑资源文件选择
const handleEditFileSelect = (event: Event) => {
  const target = event.target as HTMLInputElement
  const files = target.files
  if (files) {
    handleEditFilesUpload(Array.from(files))
  }
  target.value = ''
}

// 处理编辑资源拖拽上传
const handleEditDropUpload = (event: DragEvent) => {
  isEditDragover.value = false
  const files = event.dataTransfer?.files
  if (files && files.length > 0) {
    handleEditFilesUpload(Array.from(files))
  }
}

// 上传编辑中的资源
const handleEditFilesUpload = async (files: File[]) => {
  if (!currentSkill.value?.name) {
    ElMessage.warning('技能名称不存在')
    return
  }

  const userStoreModule = await import('@/stores/user')
  const userStoreInstance = userStoreModule.useUserStore()
  const userId = userStoreInstance.user?.id ? Number(userStoreInstance.user.id) : undefined

  for (const file of files) {
    try {
      const resourceType = getResourceType(file)
      const result = await uploadResource(file, currentSkill.value.name, resourceType, '', userId)
      if (result.success && result.data) {
        currentSkillResources.value.push({
          resourceName: result.data.resourceName,
          resourceType: result.data.resourceType as any,
          storageType: result.data.storageType,
          resourcePath: result.data.resourcePath,
          resourceUrl: result.data.resourceUrl,
          fileSize: result.data.fileSize,
          mimeType: result.data.mimeType
        })
        ElMessage.success(`资源 "${result.data.resourceName}" 上传成功`)
      } else {
        ElMessage.error(result.error || '上传失败')
      }
    } catch (error) {
      ElMessage.error('上传失败')
      console.error(error)
    }
  }
}

// 保存详情修改
const saveDetailChanges = async () => {
  if (!currentSkill.value) return

  try {
    isSavingDetail.value = true

    // 获取当前用户ID
    const userStoreModule = await import('@/stores/user')
    const userStoreInstance = userStoreModule.useUserStore()
    const userId = userStoreInstance.user?.id ? Number(userStoreInstance.user.id) : undefined

    // 保存基本信息修改
    if (hasBasicInfoChanged.value && currentSkill.value) {
      const config: SkillConfig = {
        name: currentSkill.value.name,
        description: currentSkill.value.description,
        category: currentSkill.value.category,
        version: currentSkill.value.version,
        tags: currentSkill.value.tags,
        allowedTools: currentSkill.value.allowedTools,
        instructions: currentSkill.value.instructions ?? '',
        priority: currentSkill.value.priority
      }
      await skillStore.updateSkill(config, currentSkill.value.name, userId)
    }

    // 保存脚本修改
    for (const script of currentSkillScripts.value) {
      if (script.isModified) {
        await skillStore.updateSkillScripts(
          currentSkill.value.name,
          script.scriptType,
          script.scriptContent,
          userId
        )
        script.isModified = false
      }
    }

    // 删除标记为删除的资源（需要后端支持）
    // 目前只处理新上传的资源

    ElMessage.success('保存成功')
    showDetailDialog.value = false

    // 刷新技能列表
    await skillStore.loadAllSkills(true)
  } catch (error) {
    ElMessage.error('保存失败')
    console.error(error)
  } finally {
    isSavingDetail.value = false
  }
}


// 判断文件类型
const getResourceType = (file: File): string => {
  if (file.type.startsWith('image/')) return 'image'
  if (file.type.includes('pdf') || file.type.includes('document') || file.type.includes('sheet')) return 'document'
  return 'other'
}

const isImageType = (resource: SkillResource): boolean => {
  const mimeType = resource.mimeType || ''
  return mimeType.startsWith('image/') ||
    ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp', 'ico'].some(ext =>
      resource.resourceName?.toLowerCase().endsWith(ext)
    )
}

const isDocumentType = (resource: SkillResource): boolean => {
  const mimeType = resource.mimeType || ''
  return mimeType.includes('pdf') || mimeType.includes('document') ||
    mimeType.includes('sheet') || mimeType.includes('text') ||
    ['pdf', 'doc', 'docx', 'txt', 'md', 'json', 'csv', 'xlsx', 'xls'].some(ext =>
      resource.resourceName?.toLowerCase().endsWith(ext)
    )
}

// 获取资源预览URL
const getResourcePreviewUrl = (resource: SkillResource): string => {
  if (resource.resourceUrl) {
    return resource.resourceUrl
  }
  // 如果是 base64 或 blob URL
  if (resource.resourcePath?.startsWith('data:') || resource.resourcePath?.startsWith('blob:')) {
    return resource.resourcePath
  }
  // 返回占位图
  return 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 24 24" fill="none" stroke="%23666" stroke-width="2"%3E%3Crect x="3" y="3" width="18" height="18" rx="2" ry="2"%3E%3C/rect%3E%3Ccircle cx="8.5" cy="8.5" r="1.5"%3E%3C/circle%3E%3Cpolyline points="21 15 16 10 5 21"%3E%3C/polyline%3E%3C/svg%3E'
}

const getNewResourceIndex = (resource: SkillResource): number => {
  return newSkillResources.value.findIndex(r => r.resourcePath === resource.resourcePath)
}

// 创建技能的文件上传方法
const triggerNewFileInput = () => {
  newFileInputRef.value?.click()
}

const handleNewFileSelect = (event: Event) => {
  const target = event.target as HTMLInputElement
  const files = target.files
  if (files) {
    handleNewFilesUpload(Array.from(files))
  }
  target.value = ''
}

const handleNewDropUpload = (event: DragEvent) => {
  isNewDragover.value = false
  const files = event.dataTransfer?.files
  if (files && files.length > 0) {
    handleNewFilesUpload(Array.from(files))
  }
}

const handleNewFilesUpload = async (files: File[]) => {
  if (!newSkill.value.name) {
    ElMessage.warning('请先填写技能名称')
    return
  }

  for (const file of files) {
    await handleNewSkillResourceUpload(file)
  }
}

const handleDownloadResource = async (resource: SkillResource) => {
  if (!currentSkill.value || !resource.resourcePath) return
  
  try {
    const result = await downloadResource(currentSkill.value.name, resource.resourcePath)
    if (result.success && result.data) {
      window.open(result.data, '_blank')
    } else {
      ElMessage.error(result.error || '获取下载链接失败')
    }
  } catch (error) {
    ElMessage.error('下载失败')
    console.error(error)
  }
}

const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const handleCreate = async () => {
  if (!newSkill.value.name || !newSkill.value.description || !newSkill.value.category) {
    ElMessage.warning('请填写必填项')
    return
  }

  try {
    isCreating.value = true
    const config: SkillConfig = {
      ...newSkill.value,
      scripts: newSkillScripts.value,
      resources: newSkillResources.value
    }
    await skillStore.createSkill(config)
    ElMessage.success('创建成功')
    showCreateDialog.value = false
    resetNewSkill()
  } catch (error) {
    ElMessage.error('创建失败')
    console.error(error)
  } finally {
    isCreating.value = false
  }
}

const resetNewSkill = () => {
  newSkill.value = {
    name: '',
    description: '',
    category: '',
    version: '1.0.0',
    tags: [],
    allowedTools: [],
    instructions: '',
    priority: 'MEDIUM'
  }
  createActiveTab.value = 'basic'
  newSkillScripts.value = []
  newSkillResources.value = []
}

const addNewScript = () => {
  newSkillScripts.value.push({
    scriptName: `script_${newSkillScripts.value.length + 1}`,
    scriptType: 'python',
    scriptContent: ''
  })
}

const removeNewScript = (index: number) => {
  newSkillScripts.value.splice(index, 1)
}

// 从模板创建新技能脚本
const handleAddNewScriptFromTemplate = (type: string) => {
  const template = scriptTemplates[type as keyof typeof scriptTemplates] || ''
  newSkillScripts.value.push({
    scriptName: `${type}_script_${newSkillScripts.value.length + 1}`,
    scriptType: type as any,
    scriptContent: template
  })
  ElMessage.success(`已创建 ${type} 脚本模板`)
}

const handleNewSkillResourceUpload = async (file: File): Promise<boolean> => {
  if (!newSkill.value.name) {
    ElMessage.warning('请先填写技能名称')
    return false
  }

  try {
    const userStoreModule = await import('@/stores/user')
    const userStoreInstance = userStoreModule.useUserStore()
    const userId = userStoreInstance.user?.id ? Number(userStoreInstance.user.id) : undefined

    const resourceType = getResourceType(file)
    const result = await uploadResource(file, newSkill.value.name, resourceType, '', userId)
    if (result.success && result.data) {
      newSkillResources.value.push({
        resourceName: result.data.resourceName,
        resourceType: result.data.resourceType as any,
        storageType: result.data.storageType,
        resourcePath: result.data.resourcePath,
        resourceUrl: result.data.resourceUrl,
        fileSize: result.data.fileSize,
        mimeType: result.data.mimeType
      })
      ElMessage.success('资源上传成功')
    } else {
      ElMessage.error(result.error || '上传失败')
    }
  } catch (error) {
    ElMessage.error('上传失败')
    console.error(error)
  }
  return false
}

const removeNewSkillResource = (index: number) => {
  newSkillResources.value.splice(index, 1)
}

const toggleCategory = (category: string) => {
  const index = activeCategories.value.indexOf(category)
  if (index > -1) {
    activeCategories.value.splice(index, 1)
  } else {
    activeCategories.value.push(category)
  }
}

const toggleScope = (scope: string) => {
  const index = expandedScopes.value.indexOf(scope)
  if (index > -1) {
    expandedScopes.value.splice(index, 1)
  } else {
    expandedScopes.value.push(scope)
  }
}
</script>

<style scoped lang="scss">
@use '@/styles/sf-theme.scss' as *;

.skill-manager {
  @include page-container;
}

.skill-manager__bg {
  @include page-background;
}

.skill-manager__main {
  @include page-main;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.skill-manager__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  max-width: 1200px;
  margin-bottom: 20px;
  gap: 20px;
  flex-wrap: wrap;

  .header-content {
    h1 {
      font-size: 24px;
      font-weight: 700;
      color: var(--chat-text-primary);
      margin: 0 0 4px 0;
      letter-spacing: -0.5px;
    }

    p {
      font-size: 14px;
      color: var(--chat-text-tertiary);
      margin: 0;
    }
  }

  .header-actions {
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
  }
}

.skill-manager__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  width: 100%;
  max-width: 1200px;
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 14px;
  transition: all 0.25s ease;

  &:hover {
    background: var(--chat-surface-glass-hover);
    border-color: var(--chat-border-default);
    transform: translateY(-2px);
  }

  &__icon {
    width: 44px;
    height: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 12px;

    svg {
      width: 22px;
      height: 22px;
    }

    &--primary {
      background: color-mix(in srgb, var(--chat-accent-purple) 15%, transparent);
      color: var(--chat-accent-purple);
    }

    &--success {
      background: color-mix(in srgb, var(--chat-accent-green) 15%, transparent);
      color: var(--chat-accent-green);
    }

    &--warning {
      background: color-mix(in srgb, var(--chat-accent-orange) 15%, transparent);
      color: var(--chat-accent-orange);
    }

    &--info {
      background: color-mix(in srgb, var(--chat-accent-cyan) 15%, transparent);
      color: var(--chat-accent-cyan);
    }
  }

  &__content {
    flex: 1;
  }

  &__value {
    font-size: 24px;
    font-weight: 700;
    color: var(--chat-text-primary);
    line-height: 1.2;
  }

  &__label {
    font-size: 12px;
    color: var(--chat-text-muted);
    margin-top: 2px;
  }
}

.skill-manager__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  max-width: 1200px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.search-box {
  flex: 1;
  max-width: 400px;
  position: relative;

  svg {
    position: absolute;
    left: 14px;
    top: 50%;
    transform: translateY(-50%);
    width: 18px;
    height: 18px;
    color: var(--chat-text-muted);
  }

  input {
    width: 100%;
    padding: 12px 14px 12px 42px;
    background: var(--chat-surface-glass);
    border: 1px solid var(--chat-border-subtle);
    border-radius: 10px;
    color: var(--chat-text-primary);
    font-size: 14px;
    transition: all 0.2s ease;

    &::placeholder {
      color: var(--chat-text-muted);
    }

    &:focus {
      outline: none;
      border-color: var(--chat-accent-purple);
      background: var(--chat-surface-glass-hover);
    }
  }
}

.tab-buttons {
  display: flex;
  gap: 4px;
  background: var(--chat-surface-glass);
  padding: 4px;
  border-radius: 10px;
}

.tab-btn {
  padding: 10px 20px;
  background: transparent;
  border: none;
  color: var(--chat-text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s ease;

  &:hover {
    color: var(--chat-text-primary);
  }

  &--active {
    background: var(--chat-surface-glass-hover);
    color: var(--chat-text-primary);
  }
}

.skill-manager__content {
  width: 100%;
  max-width: 1200px;
  flex: 1;
}

.skills-by-scope {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.scope-section {
  background: var(--chat-surface-glass);
  border-radius: 12px;
  overflow: hidden;
}

.scope-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  cursor: pointer;
  background: var(--chat-surface-hover);
  transition: background 0.2s ease;

  &:hover {
    background: color-mix(in srgb, var(--chat-accent-primary) 10%, var(--chat-surface-hover));
  }
}

.scope-header__left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.scope-count {
  font-size: 13px;
  color: var(--chat-text-secondary);
}

.scope-arrow {
  width: 20px;
  height: 20px;
  color: var(--chat-text-secondary);
  transition: transform 0.3s ease;

  &--open {
    transform: rotate(180deg);
  }
}

.skills-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 16px;
  padding: 16px;
}

.skill-card {
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 14px;
  padding: 18px;
  cursor: pointer;
  transition: all 0.25s ease;

  &:hover {
    background: var(--chat-surface-glass-hover);
    border-color: var(--chat-border-default);
    transform: translateY(-2px);
    box-shadow: var(--chat-shadow-md);
  }

  &--selected {
    border-color: var(--chat-accent-purple);
    background: color-mix(in srgb, var(--chat-accent-purple) 5%, transparent);
  }

  &__header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 10px;
  }

  &__title {
    display: flex;
    align-items: center;
    gap: 10px;

    h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
      color: var(--chat-text-primary);
    }
  }

  &__desc {
    color: var(--chat-text-tertiary);
    font-size: 13px;
    line-height: 1.6;
    margin: 0 0 12px 0;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  &__meta {
    display: flex;
    gap: 16px;
    margin-bottom: 12px;

    .meta-item {
      display: flex;
      align-items: center;
      gap: 6px;
      color: var(--chat-text-muted);
      font-size: 12px;

      svg {
        width: 14px;
        height: 14px;
      }
    }
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-bottom: 12px;

    .tag {
      font-size: 11px;
      padding: 3px 8px;
      background: var(--chat-surface-glass);
      border-radius: 4px;
      color: var(--chat-text-tertiary);
    }
  }

  &__stats {
    display: flex;
    justify-content: space-between;
    padding-top: 12px;
    border-top: 1px solid var(--chat-border-subtle);
    margin-bottom: 12px;

    .stat {
      display: flex;
      flex-direction: column;
      gap: 2px;

      &__label {
        font-size: 11px;
        color: var(--chat-text-muted);
      }

      &__value {
        font-size: 14px;
        font-weight: 600;
        color: var(--chat-text-primary);
      }
    }
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }
}

.priority-badge {
  font-size: 10px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 4px;
  text-transform: uppercase;

  &--low {
    background: var(--chat-surface-glass);
    color: var(--chat-text-secondary);
  }

  &--medium {
    background: color-mix(in srgb, var(--chat-accent-cyan) 20%, transparent);
    color: var(--chat-accent-cyan);
  }

  &--high {
    background: color-mix(in srgb, var(--chat-accent-orange) 20%, transparent);
    color: var(--chat-accent-orange);
  }

  &--critical {
    background: color-mix(in srgb, var(--chat-accent-red) 20%, transparent);
    color: var(--chat-accent-red);
  }
}

.scope-badge {
  font-size: 10px;
  font-weight: 500;
  padding: 3px 8px;
  border-radius: 4px;
  text-transform: uppercase;

  &--system {
    background: color-mix(in srgb, var(--chat-accent-red) 15%, transparent);
    color: var(--chat-accent-red);
  }

  &--user {
    background: color-mix(in srgb, var(--chat-accent-green) 15%, transparent);
    color: var(--chat-accent-green);
  }

  &--project {
    background: color-mix(in srgb, var(--chat-accent-orange) 15%, transparent);
    color: var(--chat-accent-orange);
  }
}

.pending-badge {
  font-size: 11px;
  padding: 4px 10px;
  background: color-mix(in srgb, var(--chat-accent-orange) 15%, transparent);
  color: var(--chat-accent-orange);
  border-radius: 6px;
}

.toggle-switch {
  position: relative;
  width: 40px;
  height: 22px;
  cursor: pointer;

  &--small {
    width: 36px;
    height: 20px;

    .toggle-slider::before {
      width: 16px;
      height: 16px;
    }

    input:checked + .toggle-slider::before {
      transform: translateX(16px);
    }
  }

  input {
    opacity: 0;
    width: 0;
    height: 0;

    &:checked + .toggle-slider {
      background: var(--chat-gradient-primary);

      &::before {
        transform: translateX(18px);
      }
    }

    &:disabled + .toggle-slider {
      opacity: 0.4;
      cursor: not-allowed;
    }
  }

  .toggle-slider {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: var(--chat-surface-glass-active);
    border-radius: 22px;
    transition: all 0.3s ease;

    &::before {
      content: '';
      position: absolute;
      width: 18px;
      height: 18px;
      left: 2px;
      bottom: 2px;
      background: var(--chat-text-primary);
      border-radius: 50%;
      transition: all 0.3s ease;
    }
  }
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  border: none;

  svg {
    width: 16px;
    height: 16px;
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  &--small {
    padding: 6px 12px;
    font-size: 12px;

    svg {
      width: 14px;
      height: 14px;
    }
  }

  &--primary {
    background: var(--chat-gradient-primary);
    color: var(--chat-accent-text);

    &:hover:not(:disabled) {
      transform: translateY(-1px);
      box-shadow: var(--chat-shadow-glow-purple);
    }
  }

  &--success {
    background: var(--chat-gradient-success);
    color: var(--chat-accent-text);

    &:hover:not(:disabled) {
      transform: translateY(-1px);
      box-shadow: 0 4px 12px color-mix(in srgb, var(--chat-accent-green) 40%, transparent);
    }
  }

  &--danger {
    background: color-mix(in srgb, var(--chat-accent-red) 15%, transparent);
    color: var(--chat-accent-red);

    &:hover:not(:disabled) {
      background: color-mix(in srgb, var(--chat-accent-red) 25%, transparent);
    }
  }

  &--ghost {
    background: transparent;
    color: var(--chat-text-secondary);

    &:hover:not(:disabled) {
      color: var(--chat-text-primary);
      background: var(--chat-surface-glass);
    }
  }
}

.category-view {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.category-section {
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 14px;
  overflow: hidden;
}

.category-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: var(--chat-surface-glass);
  }

  h3 {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: var(--chat-text-primary);
    flex: 1;
  }

  .category-count {
    font-size: 12px;
    padding: 2px 8px;
    background: var(--chat-surface-glass);
    border-radius: 4px;
    color: var(--chat-text-tertiary);
  }

  .category-arrow {
    width: 18px;
    height: 18px;
    color: var(--chat-text-muted);
    transition: transform 0.2s ease;

    &--open {
      transform: rotate(180deg);
    }
  }
}

.category-content {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
  padding: 0 16px 16px;
}

.skill-card-mini {
  background: color-mix(in srgb, var(--chat-surface-glass) 50%, transparent);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 10px;
  padding: 14px;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: var(--chat-surface-glass);
    border-color: var(--chat-border-default);
  }

  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 6px;

    h4 {
      margin: 0;
      font-size: 14px;
      font-weight: 600;
      color: var(--chat-text-primary);
    }
  }

  &__desc {
    margin: 0;
    color: var(--chat-text-tertiary);
    font-size: 12px;
    line-height: 1.5;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
}

.suggest-view {
  .suggest-input {
    display: flex;
    flex-direction: column;
    gap: 12px;
    margin-bottom: 24px;

    textarea {
      width: 100%;
      padding: 14px 16px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 10px;
      color: white;
      font-size: 14px;
      line-height: 1.6;
      resize: vertical;

      &::placeholder {
        color: rgba(255, 255, 255, 0.3);
      }

      &:focus {
        outline: none;
        border-color: rgba(102, 126, 234, 0.5);
      }
    }
  }

  .suggested-skills {
    h3 {
      font-size: 16px;
      font-weight: 600;
      color: white;
      margin: 0 0 16px 0;
    }

    display: flex;
    flex-direction: column;
    gap: 12px;
  }
}

:deep(.skill-dialog) {
  .el-dialog {
    background: var(--chat-bg-elevated);
    border: 1px solid var(--chat-border-subtle);
    border-radius: 16px;

    .el-dialog__header {
      padding: 20px 24px;
      border-bottom: 1px solid var(--chat-border-subtle);

      .el-dialog__title {
        color: var(--chat-text-primary);
        font-weight: 600;
      }
    }

    .el-dialog__body {
      padding: 24px;
    }

    .el-dialog__footer {
      padding: 16px 24px;
      border-top: 1px solid var(--chat-border-subtle);
    }
  }

  .el-form-item__label {
    color: var(--chat-text-secondary);
  }

  .el-input__wrapper,
  .el-select__wrapper {
    background: var(--chat-surface-glass);
    border: 1px solid var(--chat-border-subtle);
    box-shadow: none;

    &:hover {
      border-color: var(--chat-border-default);
    }

    &.is-focus {
      border-color: var(--chat-accent-purple);
    }
  }

  .el-input__inner {
    color: var(--chat-text-primary);

    &::placeholder {
      color: var(--chat-text-muted);
    }
  }

  .el-textarea__inner {
    background: var(--chat-surface-glass);
    border: 1px solid var(--chat-border-subtle);
    color: var(--chat-text-primary);

    &:focus {
      border-color: var(--chat-accent-purple);
    }
  }
}

@media (max-width: 1024px) {
  .skill-manager__stats {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .skill-manager__main {
    padding: 16px;
  }

  .skill-manager__header {
    flex-direction: column;
    align-items: flex-start;

    .header-actions {
      width: 100%;
      justify-content: flex-end;
    }
  }

  .skill-manager__stats {
    grid-template-columns: repeat(2, 1fr);
  }

  .skill-manager__toolbar {
    flex-direction: column;

    .search-box {
      max-width: 100%;
    }

    .tab-buttons {
      width: 100%;
      justify-content: center;
    }
  }

  .skills-grid {
    grid-template-columns: 1fr;
  }
}

// 资源上传拖拽区域
.upload-dropzone {
  border: 2px dashed var(--chat-border-subtle);
  border-radius: 12px;
  padding: 32px 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.25s ease;
  margin-bottom: 16px;
  background: var(--chat-surface-glass);

  &:hover,
  &--dragover {
    border-color: var(--chat-accent-purple);
    background: color-mix(in srgb, var(--chat-accent-purple) 5%, transparent);
  }

  &--dragover {
    transform: scale(1.01);
  }

  .upload-icon {
    width: 48px;
    height: 48px;
    color: var(--chat-text-muted);
    margin-bottom: 12px;
  }

  .upload-text {
    font-size: 14px;
    color: var(--chat-text-secondary);
    margin: 0 0 8px 0;

    .upload-link {
      color: var(--chat-accent-purple);
      font-weight: 500;
    }
  }

  .upload-hint {
    font-size: 12px;
    color: var(--chat-text-muted);
    margin: 0;
  }
}

// 资源类型选择器
.resource-type-selector {
  display: flex;
  gap: 8px;
}

// 图片预览网格
.image-preview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.image-preview-item {
  position: relative;
  aspect-ratio: 1;
  border-radius: 10px;
  overflow: hidden;
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .image-preview-overlay {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    padding: 8px;
    background: linear-gradient(transparent, rgba(0, 0, 0, 0.7));
    display: flex;
    flex-direction: column;
    gap: 4px;
    opacity: 0;
    transition: opacity 0.2s ease;
  }

  &:hover .image-preview-overlay {
    opacity: 1;
  }

  .image-name {
    font-size: 11px;
    color: white;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .image-actions {
    display: flex;
    gap: 8px;
  }
}

// 资源列表
.resources-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.resource-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 10px;
  transition: all 0.2s ease;

  &:hover {
    background: var(--chat-surface-glass-hover);
    border-color: var(--chat-border-default);
  }

  &__icon {
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: color-mix(in srgb, var(--chat-accent-primary) 10%, transparent);
    border-radius: 8px;

    svg {
      width: 20px;
      height: 20px;
      color: var(--chat-accent-primary);
    }
  }

  &__info {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;

    .resource-name {
      font-size: 13px;
      font-weight: 500;
      color: var(--chat-text-primary);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .resource-meta {
      font-size: 11px;
      color: var(--chat-text-muted);
    }
  }

  &__actions {
    display: flex;
    gap: 8px;
  }
}

// 脚本编辑器样式
.scripts-section {
  .scripts-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    h4 {
      margin: 0;
      font-size: 14px;
      font-weight: 600;
      color: var(--chat-text-primary);
    }
  }

  .scripts-actions {
    display: flex;
    gap: 8px;
    align-items: center;
  }
}

.resources-section {
  .resources-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    h4 {
      margin: 0;
      font-size: 14px;
      font-weight: 600;
      color: var(--chat-text-primary);
    }
  }

  .resources-actions {
    display: flex;
    gap: 8px;
    align-items: center;
  }
}

.script-item {
  margin-bottom: 16px;
  padding: 16px;
  background: var(--chat-surface-glass);
  border: 1px solid var(--chat-border-subtle);
  border-radius: 12px;

  &__header {
    display: flex;
    gap: 12px;
    align-items: center;
    margin-bottom: 12px;
    flex-wrap: wrap;
  }

  &__name {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__type {
    flex-shrink: 0;
  }

  &__actions {
    display: flex;
    gap: 8px;
    margin-left: auto;
  }
}

.script-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;

  &--python { color: #3776ab; }
  &--bash { color: #4eaa25; }
  &--javascript { color: #f7df1e; }
  &--typescript { color: #3178c6; }
  &--ruby { color: #cc342d; }
}

.script-type-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.script-type-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;

  &--python { background: #3776ab; }
  &--bash { background: #4eaa25; }
  &--javascript { background: #f7df1e; }
  &--typescript { background: #3178c6; }
  &--ruby { background: #cc342d; }
}

.script-editor {
  &__toolbar {
    display: flex;
    gap: 16px;
    align-items: center;
    padding: 8px 12px;
    background: color-mix(in srgb, var(--chat-surface-glass) 50%, transparent);
    border-radius: 8px;
    margin-bottom: 8px;

    .line-count,
    .char-count {
      font-size: 11px;
      color: var(--chat-text-muted);
    }
  }
}

.modified-badge {
  font-size: 10px;
  padding: 2px 6px;
  background: color-mix(in srgb, var(--chat-accent-orange) 20%, transparent);
  color: var(--chat-accent-orange);
  border-radius: 4px;
  margin-left: 8px;
}

.script-code {
  :deep(.el-textarea__inner) {
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', monospace;
    font-size: 13px;
    line-height: 1.6;
    background: color-mix(in srgb, var(--chat-surface-glass) 80%, transparent);
    border-radius: 8px;
  }

  &--editable {
    :deep(.el-textarea__inner) {
      border: 1px solid var(--chat-border-default);
      &:hover {
        border-color: var(--chat-accent-purple);
      }
      &:focus {
        border-color: var(--chat-accent-purple);
        box-shadow: 0 0 0 2px color-mix(in srgb, var(--chat-accent-purple) 20%, transparent);
      }
    }
  }
}

.empty-hint {
  text-align: center;
  padding: 40px 20px;
  color: var(--chat-text-muted);
  font-size: 14px;
  background: var(--chat-surface-glass);
  border-radius: 12px;
  border: 1px dashed var(--chat-border-subtle);

  &__icon {
    margin-bottom: 12px;

    svg {
      width: 48px;
      height: 48px;
      opacity: 0.5;
    }
  }

  p {
    margin: 0;
  }

  &__sub {
    font-size: 12px;
    margin-top: 4px !important;
    opacity: 0.7;
  }
}

// 导入 dialog
.import-dialog__content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.upload-dropzone {
  &--active {
    border-color: var(--chat-accent-purple);
    background: color-mix(in srgb, var(--chat-accent-purple) 5%, transparent);
    transform: scale(1.01);
  }

  &--disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  &__icon {
    width: 40px;
    height: 40px;
    color: var(--chat-text-muted);
    margin-bottom: 8px;
  }

  &__text {
    font-size: 14px;
    color: var(--chat-text-secondary);
    margin: 0 0 4px 0;
  }

  &__hint {
    font-size: 12px;
    color: var(--chat-text-muted);
    margin: 0;
  }
}

.import-result {
  padding: 16px;
  border-radius: var(--sf-radius-lg, 12px);
  border: 1px solid;

  &--success {
    background: color-mix(in srgb, var(--sf-success, #10b981) 8%, transparent);
    border-color: color-mix(in srgb, var(--sf-success, #10b981) 20%, transparent);
  }

  &--error {
    background: color-mix(in srgb, var(--sf-error, #ef4444) 8%, transparent);
    border-color: color-mix(in srgb, var(--sf-error, #ef4444) 20%, transparent);
  }

  &__header {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 600;
    font-size: 15px;
    margin-bottom: 8px;

    svg {
      width: 20px;
      height: 20px;
    }
  }

  &--success &__header {
    color: var(--sf-success, #10b981);
  }

  &--error &__header {
    color: var(--sf-error, #ef4444);
  }

  &__message {
    font-size: 13px;
    color: var(--chat-text-secondary);
    margin: 0;
  }

  &__details {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    margin-top: 8px;
    font-size: 12px;
    color: var(--chat-text-muted);
  }
}

// 预览模式
.preview-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;

  .tag {
    padding: 2px 10px;
    border-radius: var(--sf-radius-full, 9999px);
    background: var(--sf-accent-light, #e6f0ff);
    color: var(--sf-accent, #0066ff);
    font-size: 12px;
  }
}

.preview-empty {
  color: var(--chat-text-muted);
  font-size: 13px;
}

.script-name-text {
  font-weight: 500;
  font-size: 14px;
  color: var(--chat-text-primary);
}

.script-type-badge {
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
  margin-left: 8px;

  &--python { background: #e8f5e9; color: #2e7d32; }
  &--bash { background: #fff3e0; color: #e65100; }
  &--javascript { background: #fffde7; color: #f57f17; }
  &--typescript { background: #e3f2fd; color: #1565c0; }
  &--ruby { background: #fce4ec; color: #c62828; }
}

@media (max-width: 768px) {
  :deep(.skill-dialog) {
    .el-dialog {
      width: calc(100% - 32px) !important;
      max-width: 900px !important;
      margin: 16px auto !important;
      border-radius: 12px;
      overflow: hidden;

      .el-dialog__header {
        padding: 12px 16px;
        flex-shrink: 0;

        .el-dialog__title {
          font-size: 15px;
        }

        .el-dialog__headerbtn {
          top: 12px;
          right: 12px;
        }
      }

      .el-dialog__body {
        padding: 16px;
        max-height: calc(100vh - 200px);
        overflow-y: auto;
        box-sizing: border-box;
      }

      .el-dialog__footer {
        padding: 12px 16px;
        flex-shrink: 0;
      }
    }

    .el-form {
      label-width: auto !important;

      .el-form-item {
        margin-bottom: 12px;

        .el-form-item__label {
          padding: 0 0 4px 0;
          font-size: 13px;
          line-height: 1.4;
          color: var(--chat-text-secondary);
        }

        .el-form-item__content {
          font-size: 14px;
        }
      }

      .el-row {
        margin-bottom: 0;

        .el-col {
          max-width: 100%;
          flex: 0 0 100%;
          margin-bottom: 8px;
        }
      }
    }

    .el-input,
    .el-select,
    .el-input-number {
      width: 100% !important;

      .el-input__wrapper,
      .el-select__wrapper {
        min-height: 40px;
        padding: 4px 12px;
      }

      .el-input__inner {
        font-size: 15px;
      }
    }

    .skill-detail {
      .el-tabs__header {
        margin-bottom: 12px;
      }

      .el-tabs__nav-scroll {
        overflow-x: auto;
      }

      .el-tabs__nav-wrap::after {
        height: 1px;
      }

      .el-tabs__item {
        padding: 0 12px;
        height: 40px;
        line-height: 40px;
        font-size: 14px;
      }

      .el-tab-pane {
        padding: 12px 0;
      }
    }

    .import-dialog__content {
      padding: 12px 0;

      .import-info {
        font-size: 13px;
      }
    }
  }
}

// 通用表单样式类
.input-full {
  width: 100%;
}

.input-medium {
  width: 200px;
}

.input-small {
  width: 130px;
}

.section-gap {
  margin-bottom: 16px;
}
</style>
