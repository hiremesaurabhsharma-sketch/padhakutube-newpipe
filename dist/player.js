/* Shared player adapter: official YouTube IFrame API and native media/HLS. */
'use strict';
(() => {
  let youtubeReady;
  const ytAPI = () => {
    if (window.YT?.Player) return Promise.resolve(window.YT);
    if (!youtubeReady) youtubeReady = new Promise((resolve,reject) => {
      const timeout=setTimeout(()=>reject(Error('Player controls could not load. The embedded player remains available.')),15000);
      const previous=window.onYouTubeIframeAPIReady;
      window.onYouTubeIframeAPIReady=()=>{clearTimeout(timeout);previous?.();resolve(window.YT);};
      const script=document.createElement('script');script.src='https://www.youtube.com/iframe_api';
      script.onerror=()=>{clearTimeout(timeout);reject(Error('Could not load YouTube controls.'));};document.head.append(script);
    });
    return youtubeReady;
  };
  class Player {
    constructor(root,{onTick=()=>{},onEnd=()=>{},onError=()=>{},onReady=()=>{}}={}) {
      this.root=root;this.onTick=onTick;this.onEnd=onEnd;this.onError=onError;this.onReady=onReady;
      this.timer=setInterval(()=>{if(this.ready)this.onTick(this.time(),this.duration(),this.paused());},500);
    }
    async embed(id,title,start=0) {
      this.kind='youtube';this.root.replaceChildren();
      const frame=document.createElement('iframe');frame.id='yt-'+crypto.randomUUID();frame.title=title;
      frame.src='https://www.youtube-nocookie.com/embed/'+encodeURIComponent(id)+'?enablejsapi=1&playsinline=1&origin='+encodeURIComponent(location.origin)+'&start='+Math.floor(start);
      frame.allow='autoplay; encrypted-media; picture-in-picture; fullscreen';frame.allowFullscreen=true;
      frame.referrerPolicy='strict-origin-when-cross-origin';this.root.append(frame);
      try {
        const YT=await ytAPI();if(this.destroyed)return;
        this.yt=new YT.Player(frame,{events:{onReady:()=>{if(this.destroyed)return;this.ready=true;this.onReady();},
          onStateChange:e=>{if(e.data===0)this.onEnd();},onError:e=>this.onError(e.data===101||e.data===150?'The creator has disabled embedded playback. Open the original video.':e.data===153?'The video provider could not identify this player. Open the original video.':'This video cannot play here. Open it on YouTube.')}});
      } catch(e){if(!this.destroyed)this.onError(e.message);}
    }
    native(url,{audio=false,poster='',captions=[],start=0}={}) {
      this.kind=audio?'audio':'video';const media=document.createElement(audio?'audio':'video');this.media=media;
      media.controls=true;media.preload='metadata';media.playsInline=true;if(!audio)media.poster=poster;
      this.root.replaceChildren();
      if(audio){const cover=document.createElement('img');cover.className='audio-cover';cover.src=poster;cover.alt='';cover.onerror=()=>cover.hidden=true;this.root.append(cover);}
      this.root.append(media);
      if('mediaSession' in navigator){for(const [action,handler] of Object.entries({play:()=>this.play(),pause:()=>this.pause(),seekbackward:()=>this.seek(this.time()-10),seekforward:()=>this.seek(this.time()+10),seekto:e=>this.seek(e.seekTime)})){try{navigator.mediaSession.setActionHandler(action,handler);}catch{}}}
      for(const c of captions.filter(c=>c.format==='vtt'&&/^https:\/\//.test(c.url||''))) {const track=document.createElement('track');track.kind='subtitles';track.src=c.url;track.srclang=c.language;track.label=c.name;media.append(track);}
      media.addEventListener('loadedmetadata',()=>{if(this.destroyed)return;if(start>0&&start<media.duration)media.currentTime=start;this.ready=true;this.onReady();});
      media.addEventListener('ended',()=>this.onEnd());media.addEventListener('error',()=>this.onError('This media source cannot play in this browser. Try a different quality or open the original.'));
      if(/\.m3u8(?:\?|$)/i.test(url)&&!media.canPlayType('application/vnd.apple.mpegurl')) {
        if(window.Hls?.isSupported()){this.hls=new Hls({maxBufferLength:30});this.hls.loadSource(url);this.hls.attachMedia(media);this.hls.on(Hls.Events.ERROR,(_,e)=>{if(e.fatal)this.onError('The HLS stream is unavailable. Open the original source.');});}
        else this.onError('HLS playback is not supported by this browser.');
      } else media.src=url;
    }
    time(){try{return this.media?.currentTime||this.yt?.getCurrentTime?.()||0;}catch{return 0;}}
    duration(){try{return this.media?.duration||this.yt?.getDuration?.()||0;}catch{return 0;}}
    paused(){return this.media?this.media.paused:this.yt?.getPlayerState?.()!==1;}
    play(){if(this.media)this.media.play()?.catch(()=>this.onError('Press play to start playback.'));else this.yt?.playVideo?.();}
    pause(){this.media?.pause();this.yt?.pauseVideo?.();}
    seek(t){t=Math.max(0,Number(t)||0);const d=this.duration();if(Number.isFinite(d)&&d>0)t=Math.min(t,d);if(this.media)this.media.currentTime=t;else this.yt?.seekTo?.(t,true);}
    speed(n){if(this.media)this.media.playbackRate=n;else this.yt?.setPlaybackRate?.(n);}
    rates(){return this.media?[.5,.75,1,1.25,1.5,1.75,2]:this.yt?.getAvailablePlaybackRates?.()||[1];}
    volume(n){if(this.media)this.media.volume=n;else this.yt?.setVolume?.(n*100);}
    async pip(){if(!this.media||this.kind==='audio'||!document.pictureInPictureEnabled)throw Error('Picture-in-picture is available for supported direct videos.');if(document.pictureInPictureElement)await document.exitPictureInPicture();else await this.media.requestPictureInPicture();}
    async fullscreen(){await this.root.requestFullscreen?.();}
    destroy(){this.destroyed=true;clearInterval(this.timer);if('mediaSession' in navigator)for(const a of ['play','pause','seekbackward','seekforward','seekto']){try{navigator.mediaSession.setActionHandler(a,null);}catch{}}this.hls?.destroy();try{this.yt?.destroy();}catch{}if(this.media){this.media.pause();this.media.removeAttribute('src');this.media.load();}this.root.replaceChildren();}
  }
  window.PadhakuPlayer=Player;
})();
