import FileBrowser from '../components/files/FileBrowser'

export default function FilesPage() {
  return (
    <div className="flex flex-col h-[calc(100vh-8rem)]">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-2xl font-semibold text-text-primary">Files</h1>
      </div>
      <FileBrowser />
    </div>
  )
}
