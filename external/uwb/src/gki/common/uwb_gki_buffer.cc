/******************************************************************************
 *
 *  Copyright (C) 1999-2012 Broadcom Corporation
 *  Copyright 2019 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/
#include "uwb_gki.h"
#include "uwb_gki_common.h"
#include "uwb_gki_int.h"

#if (GKI_NUM_TOTAL_BUF_POOLS > 16)
#error Number of pools out of range (16 Max)!
#endif

#include "uci_log.h"

/*******************************************************************************
**
** Function         phUwb_phUwb_gki_init_free_queue
**
** Description      Internal function called at startup to initialize a free
**                  queue. It is called once for each free queue.
**
** Returns          void
**
*******************************************************************************/
static void phUwb_gki_init_free_queue(uint8_t id, uint16_t size, uint16_t total,
                                      void* p_mem) {
  uint16_t i;
  uint16_t act_size;
  BUFFER_HDR_T* hdr;
  BUFFER_HDR_T* hdr1 = NULL;
  uint32_t* magic;
  int32_t tempsize = size;
  tGKI_COM_CB* p_cb = &gki_cb.com;

  /* Ensure an even number of longwords */
  tempsize = (int32_t)ALIGN_POOL(size);
  act_size = (uint16_t)(tempsize + BUFFER_PADDING_SIZE);

  /* Remember pool start and end addresses */
  if (p_mem) {
    p_cb->pool_start[id] = (uint8_t*)p_mem;
    p_cb->pool_end[id] = (uint8_t*)p_mem + (act_size * total);
  }

  p_cb->pool_size[id] = act_size;

  p_cb->freeq[id].size = (uint16_t)tempsize;
  p_cb->freeq[id].total = total;
  p_cb->freeq[id].cur_cnt = 0;
  p_cb->freeq[id].max_cnt = 0;

  /* Initialize  index table */
  if (p_mem) {
    hdr = (BUFFER_HDR_T*)p_mem;
    p_cb->freeq[id].p_first = hdr;
    for (i = 0; i < total; i++) {
      hdr->task_id = GKI_INVALID_TASK;
      hdr->q_id = id;
      hdr->status = BUF_STATUS_FREE;
      magic = (uint32_t*)((uint8_t*)hdr + BUFFER_HDR_SIZE + tempsize);
      *magic = MAGIC_NO;
      hdr1 = hdr;
      hdr = (BUFFER_HDR_T*)((uint8_t*)hdr + act_size);
      hdr1->p_next = hdr;
    }
    if (hdr1 != NULL) hdr = hdr1;
    hdr->p_next = NULL;
    p_cb->freeq[id].p_last = hdr;
  }
  return;
}

static bool phUwb_gki_alloc_free_queue(uint8_t id) {
  FREE_QUEUE_T* Q;
  tGKI_COM_CB* p_cb = &gki_cb.com;

  Q = &p_cb->freeq[p_cb->pool_list[id]];

  if (Q->p_first == 0) {
    void* p_mem =
        phUwb_GKI_os_malloc((Q->size + BUFFER_PADDING_SIZE) * Q->total);
    if (p_mem) {
      // re-initialize the queue with allocated memory
      phUwb_gki_init_free_queue(id, Q->size, Q->total, p_mem);
      return true;
    }
    phUwb_GKI_exception(GKI_ERROR_BUF_SIZE_TOOBIG,
                        "gki_alloc_free_queue: Not enough memory");
  }
  return false;
}

/*******************************************************************************
**
** Function         phUwb_gki_buffer_init
**
** Description      Called once internally by GKI at startup to initialize all
**                  buffers and free buffer pools.
**
** Returns          void
**
*******************************************************************************/
void phUwb_gki_buffer_init(void) {
  uint8_t i, tt, mb;
  tGKI_COM_CB* p_cb = &gki_cb.com;

  /* Initialize mailboxes */
  for (tt = 0; tt < GKI_MAX_TASKS; tt++) {
    for (mb = 0; mb < NUM_TASK_MBOX; mb++) {
      p_cb->OSTaskQFirst[tt][mb] = NULL;
      p_cb->OSTaskQLast[tt][mb] = NULL;
    }
  }

  for (tt = 0; tt < GKI_NUM_TOTAL_BUF_POOLS; tt++) {
    p_cb->pool_start[tt] = NULL;
    p_cb->pool_end[tt] = NULL;
    p_cb->pool_size[tt] = 0;

    p_cb->freeq[tt].p_first = 0;
    p_cb->freeq[tt].p_last = 0;
    p_cb->freeq[tt].size = 0;
    p_cb->freeq[tt].total = 0;
    p_cb->freeq[tt].cur_cnt = 0;
    p_cb->freeq[tt].max_cnt = 0;
  }

  /* Use default from target.h */
  p_cb->pool_access_mask = GKI_DEF_BUFPOOL_PERM_MASK;

#if (GKI_NUM_FIXED_BUF_POOLS > 0)
  phUwb_gki_init_free_queue(0, GKI_BUF0_SIZE, GKI_BUF0_MAX, p_cb->bufpool0);
#endif

#if (GKI_NUM_FIXED_BUF_POOLS > 1)
  phUwb_gki_init_free_queue(1, GKI_BUF1_SIZE, GKI_BUF1_MAX, p_cb->bufpool1);
#endif

#if (GKI_NUM_FIXED_BUF_POOLS > 2)
  phUwb_gki_init_free_queue(2, GKI_BUF2_SIZE, GKI_BUF2_MAX, p_cb->bufpool2);
#endif

#if (GKI_NUM_FIXED_BUF_POOLS > 3)
  phUwb_gki_init_free_queue(3, GKI_BUF3_SIZE, GKI_BUF3_MAX, p_cb->bufpool3);
#endif

  /* add pools to the pool_list which is arranged in the order of size */
  for (i = 0; i < GKI_NUM_FIXED_BUF_POOLS; i++) {
    p_cb->pool_list[i] = i;
  }

  p_cb->curr_total_no_of_pools = GKI_NUM_FIXED_BUF_POOLS;

  return;
}

/*******************************************************************************
**
** Function         phUwb_GKI_init_q
**
** Description      Called by an application to initialize a buffer queue.
**
** Returns          void
**
*******************************************************************************/
void phUwb_GKI_init_q(BUFFER_Q* p_q) {
  p_q->p_first = p_q->p_last = NULL;
  p_q->count = 0;

  return;
}

/*******************************************************************************
**
** Function         phUwb_GKI_getbuf
**
** Description      Called by an application to get a free buffer which
**                  is of size greater or equal to the requested size.
**
**                  Note: This routine only takes buffers from public pools.
**                        It will not use any buffers from pools
**                        marked GKI_RESTRICTED_POOL.
**
** Parameters       size - (input) number of bytes needed.
**
** Returns          A pointer to the buffer, or NULL if none available
**
*******************************************************************************/
void* phUwb_GKI_getbuf(uint16_t size) {
  uint8_t i;
  FREE_QUEUE_T* Q;
  BUFFER_HDR_T* p_hdr;
  tGKI_COM_CB* p_cb = &gki_cb.com;

  if (size == 0) {
    phUwb_GKI_exception(GKI_ERROR_BUF_SIZE_ZERO, "getbuf: Size is zero");
    return (NULL);
  }

  /* Find the first buffer pool that is public that can hold the desired size */
  for (i = 0; i < p_cb->curr_total_no_of_pools; i++) {
    if (size <= p_cb->freeq[p_cb->pool_list[i]].size) break;
  }

  if (i == p_cb->curr_total_no_of_pools) {
    phUwb_GKI_exception(GKI_ERROR_BUF_SIZE_TOOBIG, "getbuf: Size is too big");
    return (NULL);
  }

  /* Make sure the buffers aren't disturbed til finished with allocation */
  phUwb_GKI_disable();

  /* search the public buffer pools that are big enough to hold the size
   * until a free buffer is found */
  for (; i < p_cb->curr_total_no_of_pools; i++) {
    /* Only look at PUBLIC buffer pools (bypass RESTRICTED pools) */
    if (((uint16_t)1 << p_cb->pool_list[i]) & p_cb->pool_access_mask) continue;

    Q = &p_cb->freeq[p_cb->pool_list[i]];
    if (Q->cur_cnt < Q->total) {
      if (Q->p_first == 0 && phUwb_gki_alloc_free_queue(i) != true) {
        UCI_TRACE_E("out of buffer");
        phUwb_GKI_enable();
        return NULL;
      }

      if (Q->p_first == 0) {
        /* phUwb_gki_alloc_free_queue() failed to alloc memory */
        UCI_TRACE_E("fail alloc free queue");
        phUwb_GKI_enable();
        return NULL;
      }

      p_hdr = Q->p_first;
      Q->p_first = p_hdr->p_next;

      if (!Q->p_first) Q->p_last = NULL;

      if (++Q->cur_cnt > Q->max_cnt) Q->max_cnt = Q->cur_cnt;

      phUwb_GKI_enable();

      p_hdr->task_id = phUwb_GKI_get_taskid();

      p_hdr->status = BUF_STATUS_UNLINKED;
      p_hdr->p_next = NULL;
      p_hdr->Type = 0;
      return ((void*)((uint8_t*)p_hdr + BUFFER_HDR_SIZE));
    }
  }

  UCI_TRACE_E("unable to allocate buffer!!!!!");

  phUwb_GKI_enable();

  return (NULL);
}

/*******************************************************************************
**
** Function         phUwb_GKI_getpoolbuf
**
** Description      Called by an application to get a free buffer from
**                  a specific buffer pool.
**
**                  Note: If there are no more buffers available from the pool,
**                        the public buffers are searched for an available
**                        buffer.
**
** Parameters       pool_id - (input) pool ID to get a buffer out of.
**
** Returns          A pointer to the buffer, or NULL if none available
**
*******************************************************************************/
void* phUwb_GKI_getpoolbuf(uint8_t pool_id) {
  FREE_QUEUE_T* Q;
  BUFFER_HDR_T* p_hdr;
  tGKI_COM_CB* p_cb = &gki_cb.com;

  if (pool_id >= GKI_NUM_TOTAL_BUF_POOLS) return (NULL);

  /* Make sure the buffers aren't disturbed til finished with allocation */
  phUwb_GKI_disable();

  Q = &p_cb->freeq[pool_id];
  if (Q->cur_cnt < Q->total) {
    if (Q->p_first == 0 && phUwb_gki_alloc_free_queue(pool_id) != true)
      return NULL;

    if (Q->p_first == 0) {
      /* gki_alloc_free_queue() failed to alloc memory */
      UCI_TRACE_E("fail alloc free queue");
      return NULL;
    }

    p_hdr = Q->p_first;
    Q->p_first = p_hdr->p_next;

    if (!Q->p_first) Q->p_last = NULL;

    if (++Q->cur_cnt > Q->max_cnt) Q->max_cnt = Q->cur_cnt;

    phUwb_GKI_enable();

    p_hdr->task_id = phUwb_GKI_get_taskid();

    p_hdr->status = BUF_STATUS_UNLINKED;
    p_hdr->p_next = NULL;
    p_hdr->Type = 0;

    return ((void*)((uint8_t*)p_hdr + BUFFER_HDR_SIZE));
  }

  /* If here, no buffers in the specified pool */
  phUwb_GKI_enable();

  /* try for free buffers in public pools */
  return (phUwb_GKI_getbuf(p_cb->freeq[pool_id].size));
}

/*******************************************************************************
**
** Function         phUwb_GKI_freebuf
**
** Description      Called by an application to return a buffer to the free
**                  pool.
**
** Parameters       p_buf - (input) address of the beginning of a buffer.
**
** Returns          void
**
*******************************************************************************/
void phUwb_GKI_freebuf(void* p_buf) {
  FREE_QUEUE_T* Q;
  BUFFER_HDR_T* p_hdr;

#if (GKI_ENABLE_BUF_CORRUPTION_CHECK == TRUE)
  if (!p_buf || phUwb_gki_chk_buf_damage(p_buf)) {
    phUwb_GKI_exception(GKI_ERROR_BUF_CORRUPTED, "Free - Buf Corrupted");
    return;
  }
#endif

  p_hdr = (BUFFER_HDR_T*)((uint8_t*)p_buf - BUFFER_HDR_SIZE);

  if (p_hdr->status != BUF_STATUS_UNLINKED) {
    phUwb_GKI_exception(GKI_ERROR_FREEBUF_BUF_LINKED, "Freeing Linked Buf");
    return;
  }

  if (p_hdr->q_id >= GKI_NUM_TOTAL_BUF_POOLS) {
    phUwb_GKI_exception(GKI_ERROR_FREEBUF_BAD_QID, "Bad Buf QId");
    return;
  }

  phUwb_GKI_disable();

  /*
  ** Release the buffer
  */
  Q = &gki_cb.com.freeq[p_hdr->q_id];
  if (Q->p_last)
    Q->p_last->p_next = p_hdr;
  else
    Q->p_first = p_hdr;

  Q->p_last = p_hdr;
  p_hdr->p_next = NULL;
  p_hdr->status = BUF_STATUS_FREE;
  p_hdr->task_id = GKI_INVALID_TASK;
  if (Q->cur_cnt > 0) Q->cur_cnt--;

  phUwb_GKI_enable();

  return;
}

/*******************************************************************************
**
** Function         phUwb_GKI_get_buf_size
**
** Description      Called by an application to get the size of a buffer.
**
** Parameters       p_buf - (input) address of the beginning of a buffer.
**
** Returns          the size of the buffer
**
*******************************************************************************/
uint16_t phUwb_GKI_get_buf_size(void* p_buf) {
  BUFFER_HDR_T* p_hdr;

  p_hdr = (BUFFER_HDR_T*)((uint8_t*)p_buf - BUFFER_HDR_SIZE);

  if ((uintptr_t)p_hdr & 1) return (0);

  if (p_hdr->q_id < GKI_NUM_TOTAL_BUF_POOLS) {
    return (gki_cb.com.freeq[p_hdr->q_id].size);
  }

  return (0);
}

/*******************************************************************************
**
** Function         phUwb_gki_chk_buf_damage
**
** Description      Called internally by OSS to check for buffer corruption.
**
** Returns          TRUE if there is a problem, else FALSE
**
*******************************************************************************/
bool phUwb_gki_chk_buf_damage(void* p_buf) {
#if (GKI_ENABLE_BUF_CORRUPTION_CHECK == TRUE)

  uint32_t* magic;
  magic = (uint32_t*)((uint8_t*)p_buf + phUwb_GKI_get_buf_size(p_buf));

  if ((uintptr_t)magic & 1) return true;

  if (*magic == MAGIC_NO) return false;

  return true;

#else

  return false;

#endif
}

/*******************************************************************************
**
** Function         phUwb_GKI_send_msg
**
** Description      Called by applications to send a buffer to a task
**
** Returns          Nothing
**
*******************************************************************************/
void phUwb_GKI_send_msg(uint8_t task_id, uint8_t mbox, void* msg) {
  BUFFER_HDR_T* p_hdr;
  tGKI_COM_CB* p_cb = &gki_cb.com;

  /* If task non-existant or not started, drop buffer */
  if ((task_id >= GKI_MAX_TASKS) || (mbox >= NUM_TASK_MBOX) ||
      (p_cb->OSRdyTbl[task_id] == TASK_DEAD)) {
    phUwb_GKI_exception(GKI_ERROR_SEND_MSG_BAD_DEST, "Sending to unknown dest");
    phUwb_GKI_freebuf(msg);
    return;
  }

#if (GKI_ENABLE_BUF_CORRUPTION_CHECK == TRUE)
  if (phUwb_gki_chk_buf_damage(msg)) {
    phUwb_GKI_exception(GKI_ERROR_BUF_CORRUPTED, "Send - Buffer corrupted");
    return;
  }
#endif

  p_hdr = (BUFFER_HDR_T*)((uint8_t*)msg - BUFFER_HDR_SIZE);

  if (p_hdr->status != BUF_STATUS_UNLINKED) {
    phUwb_GKI_exception(GKI_ERROR_SEND_MSG_BUF_LINKED, "Send - buffer linked");
    return;
  }

  phUwb_GKI_disable();

  if (p_cb->OSTaskQFirst[task_id][mbox])
    p_cb->OSTaskQLast[task_id][mbox]->p_next = p_hdr;
  else
    p_cb->OSTaskQFirst[task_id][mbox] = p_hdr;

  p_cb->OSTaskQLast[task_id][mbox] = p_hdr;

  p_hdr->p_next = NULL;
  p_hdr->status = BUF_STATUS_QUEUED;
  p_hdr->task_id = task_id;

  phUwb_GKI_enable();

  phUwb_GKI_send_event(task_id, (uint16_t)EVENT_MASK(mbox));

  return;
}

/*******************************************************************************
**
** Function         phUwb_GKI_read_mbox
**
** Description      Called by applications to read a buffer from one of
**                  the task mailboxes.  A task can only read its own mailbox.
**
** Parameters:      mbox  - (input) mailbox ID to read (0, 1, 2, or 3)
**
** Returns          NULL if the mailbox was empty, else the address of a buffer
**
*******************************************************************************/
void* phUwb_GKI_read_mbox(uint8_t mbox) {
  uint8_t task_id = phUwb_GKI_get_taskid();
  void* p_buf = NULL;
  BUFFER_HDR_T* p_hdr;

  if ((task_id >= GKI_MAX_TASKS) || (mbox >= NUM_TASK_MBOX)) return (NULL);

  phUwb_GKI_disable();

  if (gki_cb.com.OSTaskQFirst[task_id][mbox]) {
    p_hdr = gki_cb.com.OSTaskQFirst[task_id][mbox];
    gki_cb.com.OSTaskQFirst[task_id][mbox] = p_hdr->p_next;

    p_hdr->p_next = NULL;
    p_hdr->status = BUF_STATUS_UNLINKED;

    p_buf = (uint8_t*)p_hdr + BUFFER_HDR_SIZE;
  }

  phUwb_GKI_enable();

  return (p_buf);
}

/*******************************************************************************
**
** Function         phUwb_GKI_enqueue
**
** Description      Enqueue a buffer at the tail of the queue
**
** Parameters:      p_q  -  (input) pointer to a queue.
**                  p_buf - (input) address of the buffer to enqueue
**
** Returns          void
**
*******************************************************************************/
void phUwb_GKI_enqueue(BUFFER_Q* p_q, void* p_buf) {
  BUFFER_HDR_T* p_hdr;

#if (GKI_ENABLE_BUF_CORRUPTION_CHECK == TRUE)
  if (phUwb_gki_chk_buf_damage(p_buf)) {
    phUwb_GKI_exception(GKI_ERROR_BUF_CORRUPTED, "Enqueue - Buffer corrupted");
    return;
  }
#endif

  p_hdr = (BUFFER_HDR_T*)((uint8_t*)p_buf - BUFFER_HDR_SIZE);

  if (p_hdr->status != BUF_STATUS_UNLINKED) {
    phUwb_GKI_exception(GKI_ERROR_ENQUEUE_BUF_LINKED,
                        "Eneueue - buf already linked");
    return;
  }

  phUwb_GKI_disable();

  /* Since the queue is exposed (C vs C++), keep the pointers in exposed format
   */
  if (p_q->p_first) {
    BUFFER_HDR_T* p_last_hdr =
        (BUFFER_HDR_T*)((uint8_t*)p_q->p_last - BUFFER_HDR_SIZE);
    p_last_hdr->p_next = p_hdr;
  } else
    p_q->p_first = p_buf;

  p_q->p_last = p_buf;
  p_q->count++;

  p_hdr->p_next = NULL;
  p_hdr->status = BUF_STATUS_QUEUED;

  phUwb_GKI_enable();

  return;
}

/*******************************************************************************
**
** Function         phUwb_GKI_dequeue
**
** Description      Dequeues a buffer from the head of a queue
**
** Parameters:      p_q  - (input) pointer to a queue.
**
** Returns          NULL if queue is empty, else buffer
**
*******************************************************************************/
void* phUwb_GKI_dequeue(BUFFER_Q* p_q) {
  BUFFER_HDR_T* p_hdr;

  phUwb_GKI_disable();

  if (!p_q || !p_q->count) {
    phUwb_GKI_enable();
    return (NULL);
  }

  p_hdr = (BUFFER_HDR_T*)((uint8_t*)p_q->p_first - BUFFER_HDR_SIZE);

  /* Keep buffers such that GKI header is invisible
   */
  if (p_hdr->p_next)
    p_q->p_first = ((uint8_t*)p_hdr->p_next + BUFFER_HDR_SIZE);
  else {
    p_q->p_first = NULL;
    p_q->p_last = NULL;
  }

  p_q->count--;

  p_hdr->p_next = NULL;
  p_hdr->status = BUF_STATUS_UNLINKED;

  phUwb_GKI_enable();

  return ((uint8_t*)p_hdr + BUFFER_HDR_SIZE);
}

/********************************************************
 * The following functions are not needed for light stack
 *********************************************************/
#ifndef BTU_STACK_LITE_ENABLED
#define BTU_STACK_LITE_ENABLED FALSE
#endif
