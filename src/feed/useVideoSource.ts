import { useEffect, type RefObject } from 'react'
import type HlsType from 'hls.js'

type Mode = 'active' | 'preload' | 'idle'

const setSource = (element: HTMLVideoElement, url: string) => element.setAttribute('src', url)

/**
 * Liga o video a melhor fonte disponivel: HLS nativo (Safari/iOS), hls.js (Android e desktop) ou o MP4 de reserva.
 * Longe da tela o video fica sem fonte, para nao gastar dados; o vizinho baixa so o inicio.
 */
export function useVideoSource(video: RefObject<HTMLVideoElement | null>, hlsUrl: string | undefined, mp4Url: string | undefined, mode: Mode) {
  const loading = mode !== 'idle'
  useEffect(() => {
    const element = video.current
    if (!element || !loading) return
    let hls: HlsType | null = null
    let cancelled = false
    const fallbackToMp4 = () => { if (mp4Url && !cancelled) setSource(element, mp4Url) }
    if (hlsUrl && element.canPlayType('application/vnd.apple.mpegurl')) {
      setSource(element, hlsUrl)
    } else if (hlsUrl) {
      void import('hls.js').then(({ default: Hls }) => {
        if (cancelled) return
        if (!Hls.isSupported()) return fallbackToMp4()
        hls = new Hls({ capLevelToPlayerSize: true, startLevel: -1, maxBufferLength: 12, backBufferLength: 10 })
        hls.on(Hls.Events.ERROR, (_event, data) => {
          if (data.fatal) { hls?.destroy(); hls = null; fallbackToMp4() }
        })
        hls.loadSource(hlsUrl)
        hls.attachMedia(element)
        if (element.dataset.active === 'true') element.play().catch(() => undefined)
      }).catch(fallbackToMp4)
    } else {
      fallbackToMp4()
    }
    return () => {
      cancelled = true
      hls?.destroy()
      element.removeAttribute('src')
      element.load()
    }
  }, [video, hlsUrl, mp4Url, loading])
}
