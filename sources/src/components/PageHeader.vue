<template>
  <section class="page-header">
    <div class="page-header__content">
      <div class="page-header__kicker">{{ kicker }}</div>
      <h2>{{ title }}</h2>
      <p v-if="description">{{ description }}</p>
    </div>
    <n-space class="page-header__actions" justify="end">
      <slot name="actions" />
    </n-space>
  </section>
</template>

<script setup>
defineProps({
  kicker: { type: String, default: '' },
  title: { type: String, required: true },
  description: { type: String, default: '' }
})
</script>

<style scoped>
.page-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: start;
  padding: 18px 20px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(148, 163, 184, 0.2);
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.05);
}

.page-header__kicker {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.14em;
  color: #0f766e;
}

.page-header h2 {
  margin: 6px 0 8px;
  font-size: 24px;
  line-height: 1.2;
}

.page-header p {
  margin: 0;
  color: #64748b;
  line-height: 1.6;
}

.page-header__actions {
  flex-wrap: wrap;
}

@media (max-width: 900px) {
  .page-header {
    grid-template-columns: 1fr;
  }
}
</style>
