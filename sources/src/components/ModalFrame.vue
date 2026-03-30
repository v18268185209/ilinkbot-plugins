<template>
  <n-modal
    :show="show"
    preset="card"
    :title="title"
    :style="modalStyle"
    :mask-closable="false"
    @update:show="$emit('update:show', $event)"
  >
    <div class="modal-frame__body" :style="bodyStyle">
      <slot />
    </div>
    <template #footer>
      <div class="modal-frame__footer">
        <slot name="footer" />
      </div>
    </template>
  </n-modal>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  show: { type: Boolean, default: false },
  title: { type: String, required: true },
  width: { type: Number, default: 720 },
  height: { type: Number, default: 560 }
})

defineEmits(['update:show'])

const modalStyle = computed(() => ({
  width: `${props.width}px`,
  maxWidth: 'calc(100vw - 32px)'
}))

const bodyStyle = computed(() => ({
  maxHeight: `${Math.max(props.height - 180, 240)}px`
}))
</script>

<style scoped>
.modal-frame__body {
  overflow: auto;
  padding-right: 4px;
}

.modal-frame__footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
