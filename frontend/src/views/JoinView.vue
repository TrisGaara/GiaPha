<template>
  <div class="page" style="max-width: 560px">
    <el-card>
      <template #header><b>Tham gia dòng họ</b></template>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="Mã tham gia">
          <el-input v-model="code" placeholder="VD: AB12CD34" @change="lookup">
            <template #append><el-button @click="lookup">Tra cứu</el-button></template>
          </el-input>
        </el-form-item>
      </el-form>

      <template v-if="family">
        <el-descriptions :column="1" border style="margin-bottom: 16px">
          <el-descriptions-item label="Dòng họ">{{ family.name }}</el-descriptions-item>
          <el-descriptions-item label="Họ">{{ family.surname }}</el-descriptions-item>
          <el-descriptions-item label="Nguyên quán">{{ family.origin || '—' }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-position="top">
          <el-form-item label="Lời nhắn cho Ban quản trị (giúp xác định vị trí của bạn trên phả hệ)">
            <el-input v-model="message" type="textarea" :rows="3"
              placeholder="VD: Tôi là Nguyễn Văn B, con trưởng ông Nguyễn Văn A (đời 12, chi 2)" maxlength="500" show-word-limit />
          </el-form-item>
          <el-button type="primary" :loading="sending" @click="send">Gửi yêu cầu tham gia</el-button>
        </el-form>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'

const route = useRoute()
const router = useRouter()
const code = ref(route.query.code || '')
const family = ref(null)
const message = ref('')
const sending = ref(false)

async function lookup() {
  family.value = null
  if (!code.value.trim()) return
  family.value = await api.get('/families/lookup', { params: { code: code.value.trim() } })
}

async function send() {
  sending.value = true
  try {
    await api.post('/join-requests', { joinCode: code.value.trim(), message: message.value })
    ElMessage.success('Đã gửi yêu cầu. Bạn sẽ nhận được thông báo khi Ban quản trị duyệt.')
    router.push('/families')
  } finally {
    sending.value = false
  }
}

onMounted(lookup)
</script>
